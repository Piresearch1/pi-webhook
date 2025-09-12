package com.payintelli.webhook.handlers;

import java.util.List;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payintelli.webhook.models.WebhookDelivery;
import com.payintelli.webhook.models.WebhookDeliveryMessage;
import com.payintelli.webhook.models.WebhookEndpoint;
import com.payintelli.webhook.models.WebhookPublisherMessage;
import com.payintelli.webhook.services.WebhookDatabaseService;
import com.payintelli.webhook.services.WebhookDynamoDbService;

public class WebhookPublisherLambda implements RequestHandler<SQSEvent, String> {

  private final AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
  private final ObjectMapper readObjectMapper;
  private final ObjectMapper writeObjectMapper = new ObjectMapper();
  private final WebhookDatabaseService dbService;
  private final String queueUrl;
  private final WebhookDynamoDbService webhookDynamoDbService;


  public WebhookPublisherLambda() {
    this.dbService = new WebhookDatabaseService(System.getenv("DATABASE_URL"),
        System.getenv("DATABASE_USERNAME"), System.getenv("DATABASE_PASSWORD"));
    this.queueUrl = System.getenv("SQS_QUEUE_URL");
    this.webhookDynamoDbService = new WebhookDynamoDbService(System.getenv("DYNAMODB_TABLE_NAME"));


    readObjectMapper = new ObjectMapper();
    readObjectMapper.registerModule(new JavaTimeModule());
    readObjectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
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

  public String processWebhookMessage(SQSEvent.SQSMessage sqsMessage, Context context) {
    try {
      WebhookPublisherMessage event =
          readObjectMapper.readValue(sqsMessage.getBody(), WebhookPublisherMessage.class);
      context.getLogger().log("Processing webhook event: " + event);

      List<WebhookEndpoint> endpoints = webhookDynamoDbService
          .findActiveEndpointsByEvent(event.getClientId(), event.getEventType());
      context.getLogger()
          .log("Found " + endpoints.size() + " endpoints for event: " + event.getEventType());

      for (WebhookEndpoint endpoint : endpoints) {
        WebhookDelivery delivery = new WebhookDelivery();
        delivery.setWebhookEndpointId(endpoint.getId());
        delivery.setEventType(event.getEventType());
        delivery.setPayload(event.getData().toString());
        delivery.setAttemptCount(1);
        delivery.setStatus("PENDING");

        Long deliveryId = dbService.createWebhookDelivery(delivery);

        dbService.insertWebhookDeliveryLog(deliveryId, 1, delivery.getPayload());

        WebhookDeliveryMessage message = new WebhookDeliveryMessage(deliveryId, endpoint.getId(),
            event.getEventType(), event.getData().toString(), 1);
        String messageBody = writeObjectMapper.writeValueAsString(message);

        SendMessageRequest sendMessageRequest =
            new SendMessageRequest().withQueueUrl(queueUrl).withMessageBody(messageBody);

        sqs.sendMessage(sendMessageRequest);

        context.getLogger()
            .log("Queued delivery " + deliveryId + " for endpoint " + endpoint.getId());
      }

      return "Published " + endpoints.size() + " webhook deliveries for event: "
          + event.getEventType();

    } catch (Exception e) {
      context.getLogger().log("Error processing webhook event: " + e.getMessage());
      throw new RuntimeException(e);
    }
  }
}
