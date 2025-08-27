package com.payintelli.webhook.handlers;

import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payintelli.webhook.models.WebhookEndpoint;
import com.payintelli.webhook.models.WebhookMessage;
import com.payintelli.webhook.services.WebhookDatabaseService;
import com.payintelli.webhook.services.WebhookHttpService;
import com.payintelli.webhook.utils.RetryUtils;

public class WebhookDeliveryLambda implements RequestHandler<SQSEvent, String> {

	private final AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
	private final ObjectMapper objectMapper = new ObjectMapper();
	private final WebhookDatabaseService dbService;
	private final WebhookHttpService httpService;
	private final String queueUrl;
	private final int maxAttempts;

	public WebhookDeliveryLambda() {
		this.dbService = new WebhookDatabaseService(System.getenv("DATABASE_URL"), System.getenv("DATABASE_USERNAME"),
				System.getenv("DATABASE_PASSWORD"));
		this.httpService = new WebhookHttpService();
		this.queueUrl = System.getenv("SQS_QUEUE_URL");
		this.maxAttempts = Integer
				.parseInt(System.getenv("MAX_ATTEMPTS") != null ? System.getenv("MAX_ATTEMPTS") : "5");
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

	private void processWebhookMessage(SQSEvent.SQSMessage sqsMessage, Context context) throws Exception {
		WebhookMessage message = objectMapper.readValue(sqsMessage.getBody(), WebhookMessage.class);
		try {

			WebhookEndpoint endpoint = dbService.findEndpointById(message.getWebhookEndpointId());
			context.getLogger().log("WebhookEndpoint::" + endpoint);
			if (endpoint == null || !endpoint.getIsActive()) {
				context.getLogger().log("Endpoint not found or inactive: " + message.getWebhookEndpointId());

				dbService.updateDelivery(message.getDeliveryId(), null, "Endpoint not found or inactive", "FAILED",
						null, message.getAttemptCount(), null, message.getPayload(), null);
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

			dbService.updateDelivery(message.getDeliveryId(), responseStatus, responseBody, status, nextRetryAt,
					message.getAttemptCount(), requestHeaders, message.getPayload(), responseHeaders);
			
			if (isSuccess) {
                context.getLogger().log("Webhook delivered successfully: " + message.getDeliveryId());
            } else {
                context.getLogger().log("Webhook delivery failed with status " + response.statusCode() + ": " + message.getDeliveryId());
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

	private void scheduleRetry(WebhookMessage message, Context context) throws Exception {
		if (message.getAttemptCount() >= maxAttempts) {

			dbService.updateDelivery(message.getDeliveryId(), null, null, "ABANDONED", null, message.getAttemptCount(),
					null, null, null);

			context.getLogger().log("Max attempts reached, abandoning delivery: " + message.getDeliveryId());

			return;
		}

		int delaySeconds = RetryUtils.getRetryDelaySeconds(message.getAttemptCount());
		Timestamp nextRetryAt = RetryUtils.calculateNextRetryTime(message.getAttemptCount());

		dbService.updateDelivery(message.getDeliveryId(), null, null, "PENDING", nextRetryAt,
				message.getAttemptCount(),null, null, null);

		WebhookMessage retryMessage = new WebhookMessage(message.getDeliveryId(), message.getWebhookEndpointId(),
				message.getEventType(), message.getPayload(), message.getAttemptCount() + 1);

		SendMessageRequest retryRequest = new SendMessageRequest().withQueueUrl(queueUrl)
				.withMessageBody(objectMapper.writeValueAsString(retryMessage)).withDelaySeconds(delaySeconds);

		sqs.sendMessage(retryRequest);

		context.getLogger().log("Scheduled retry " + retryMessage.getAttemptCount() + " for delivery "
				+ message.getDeliveryId() + " in " + delaySeconds + " seconds");
	}
}
