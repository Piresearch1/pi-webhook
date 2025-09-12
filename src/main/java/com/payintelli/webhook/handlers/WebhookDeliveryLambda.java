package com.payintelli.webhook.handlers;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payintelli.webhook.models.WebhookDeliveryMessage;
import com.payintelli.webhook.models.WebhookEndpoint;
import com.payintelli.webhook.services.WebhookDatabaseService;
import com.payintelli.webhook.services.WebhookDynamoDbService;
import com.payintelli.webhook.services.WebhookHttpService;
import com.payintelli.webhook.utils.RetryUtils;

import software.amazon.awssdk.services.scheduler.SchedulerClient;
import software.amazon.awssdk.services.scheduler.model.CreateScheduleRequest;
import software.amazon.awssdk.services.scheduler.model.FlexibleTimeWindow;
import software.amazon.awssdk.services.scheduler.model.Target;

public class WebhookDeliveryLambda implements RequestHandler<SQSEvent, String> {

  private final AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WebhookDatabaseService dbService;
  private final WebhookHttpService httpService;
  private final String queueUrl;
  private final int maxAttempts;
  private final WebhookDynamoDbService webhookDynamoDbService;

  public WebhookDeliveryLambda() {
    this.dbService = new WebhookDatabaseService(System.getenv("DATABASE_URL"),
        System.getenv("DATABASE_USERNAME"), System.getenv("DATABASE_PASSWORD"));
    this.httpService = new WebhookHttpService();
    this.queueUrl = System.getenv("SQS_QUEUE_URL");
    this.maxAttempts = Integer
        .parseInt(System.getenv("MAX_ATTEMPTS") != null ? System.getenv("MAX_ATTEMPTS") : "5");
    
    this.webhookDynamoDbService = new WebhookDynamoDbService(System.getenv("DYNAMODB_TABLE_NAME"));
  }

  @Override
  public String handleRequest(SQSEvent event, Context context) {
    for (SQSEvent.SQSMessage message : event.getRecords()) {
      try {
        processWebhookMessage(message, context);
      } catch (Exception e) {
        context.getLogger().log("Error processing message: " + e.getMessage());
      }
    }
    return "Processed " + event.getRecords().size() + " messages";
  }

  private void processWebhookMessage(SQSEvent.SQSMessage sqsMessage, Context context)
      throws Exception {
    WebhookDeliveryMessage message =
        objectMapper.readValue(sqsMessage.getBody(), WebhookDeliveryMessage.class);
    try {
      WebhookEndpoint endpoint = webhookDynamoDbService.findEndpointById(message.getWebhookEndpointId());
      context.getLogger().log("WebhookEndpoint::" + endpoint);
      context.getLogger().log("WebhookDeliveryMessage::" + message);
      if (endpoint == null || !endpoint.getIsActive()) {
        context.getLogger()
            .log("Endpoint not found or inactive: " + message.getWebhookEndpointId());

        dbService.updateDelivery(message.getDeliveryId(), null, "Endpoint not found or inactive",
            "FAILED", null, message.getAttemptCount(), null, message.getPayload(), null);
        return;
      }

      HttpResponse<String> response = httpService.sendWebhook(endpoint, message);

      int responseStatus = response.statusCode();
      String responseBody = response.body();
      String requestHeaders = objectMapper.writeValueAsString(response.request().headers().map());
      String responseHeaders = objectMapper.writeValueAsString(response.headers().map());

      boolean isSuccess = response.statusCode() >= 200 && response.statusCode() < 300;
      String status = isSuccess ? "DELIVERED" : "FAILED";

      Timestamp nextRetryAt = ("FAILED".equals(status))
          ? Timestamp.from(Instant.now().plusSeconds(60L * message.getAttemptCount()))
          : null;

      dbService.updateDelivery(message.getDeliveryId(), responseStatus, responseBody, status,
          nextRetryAt, message.getAttemptCount(), requestHeaders, message.getPayload(),
          responseHeaders);

      if (isSuccess) {
        context.getLogger().log("Webhook delivered successfully: " + message.getDeliveryId());
      } else {
        context.getLogger().log("Webhook delivery failed with status " + response.statusCode()
            + ": " + message.getDeliveryId());
        scheduleRetry(message, context);
      }

      return;

    } catch (Exception e) {
      context.getLogger().log("Error delivering webhook: " + e.getMessage());

      try {
        dbService.updateDelivery(message.getDeliveryId(), null, e.getMessage(), "FAILED",
            Timestamp.from(Instant.now().plusSeconds(60L * message.getAttemptCount())),
            message.getAttemptCount(), null, message.getPayload(), null);

        scheduleRetry(message, context);
      } catch (Exception dbEx) {
        context.getLogger().log("Error writing audit log: " + dbEx.getMessage());
      }

      return;
    }
  }

  private void scheduleRetry(WebhookDeliveryMessage message, Context context) throws Exception {
    if (message.getAttemptCount() >= maxAttempts) {
      dbService.updateDelivery(message.getDeliveryId(), null, null, "ABANDONED", null,
          message.getAttemptCount(), null, null, null);

      context.getLogger()
          .log("Max attempts reached, abandoning delivery: " + message.getDeliveryId());
      return;
    }

    int delaySeconds = RetryUtils.getRetryDelaySeconds(message.getAttemptCount());
    Timestamp nextRetryAt = RetryUtils.calculateNextRetryTime(message.getAttemptCount());

    dbService.updateDelivery(message.getDeliveryId(), null, null, "PENDING", nextRetryAt,
        message.getAttemptCount(), null, null, null);

    WebhookDeliveryMessage retryMessage =
        new WebhookDeliveryMessage(message.getDeliveryId(), message.getWebhookEndpointId(),
            message.getEventType(), message.getPayload(), message.getAttemptCount() + 1);

    String payload = objectMapper.writeValueAsString(retryMessage);

    if (delaySeconds <= 900) {
      // ✅ Simple SQS delay (works for <= 15 min)
      SendMessageRequest retryRequest = new SendMessageRequest().withQueueUrl(queueUrl)
          .withMessageBody(payload).withDelaySeconds(delaySeconds);

      sqs.sendMessage(retryRequest);

      context.getLogger().log("Scheduled retry " + retryMessage.getAttemptCount() + " for delivery "
          + message.getDeliveryId() + " in " + delaySeconds + " seconds via SQS");
    } else {
      // ✅ Use EventBridge Scheduler for > 15 min
      SchedulerClient scheduler = SchedulerClient.create();

      ZoneId localZone = ZoneId.systemDefault();
      ZonedDateTime localTime = ZonedDateTime.now(localZone).plusMinutes(2);

      // Convert to UTC before formatting
      ZonedDateTime utcTime = localTime.withZoneSameInstant(ZoneOffset.UTC);

      DateTimeFormatter formatter =
          DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss").withZone(ZoneOffset.UTC);

      String scheduleExpression = "at(" + formatter.format(utcTime) + ")";

      String schedularNameFormat = System.getenv("SCHEDULE_ROLE_FORMAT");
      String scheduleName = String.format(schedularNameFormat, message.getDeliveryId(),
          retryMessage.getAttemptCount());

      CreateScheduleRequest scheduleRequest = CreateScheduleRequest.builder().name(scheduleName)
          .scheduleExpression(scheduleExpression).groupName(System.getenv("SCHEDULE_ROLE_GROUP"))
          .flexibleTimeWindow(FlexibleTimeWindow.builder().mode("OFF").build())
          .target(Target.builder().arn(System.getenv("SQS_QUEUE_ARN"))
              .roleArn(System.getenv("EVENTBRIDGE_ROLE_ARN")).input(payload).build())
          .build();

      scheduler.createSchedule(scheduleRequest);
      scheduler.close();

      context.getLogger()
          .log("Scheduled retry " + retryMessage.getAttemptCount() + " for delivery "
              + message.getDeliveryId() + " at " + formatter.format(utcTime)
              + " via EventBridge Scheduler");
    }
  }

}
