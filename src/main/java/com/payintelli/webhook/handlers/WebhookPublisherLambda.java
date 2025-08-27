package com.payintelli.webhook.handlers;

import java.util.List;
import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payintelli.webhook.models.WebhookDelivery;
import com.payintelli.webhook.models.WebhookEndpoint;
import com.payintelli.webhook.models.WebhookMessage;
import com.payintelli.webhook.services.WebhookDatabaseService;

public class WebhookPublisherLambda implements RequestHandler<Map<String, Object>, String> {

    private final AmazonSQS sqs = AmazonSQSClientBuilder.defaultClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final WebhookDatabaseService dbService;
    private final String queueUrl;

    public WebhookPublisherLambda() {
        this.dbService = new WebhookDatabaseService(
                System.getenv("DATABASE_URL"),
                System.getenv("DATABASE_USERNAME"),
                System.getenv("DATABASE_PASSWORD"));
        this.queueUrl = System.getenv("SQS_QUEUE_URL");
    }

    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        try {
            String eventType = (String) event.get("eventType");
            String clientId = (String) event.get("clientId");
            Object eventData = event.get("data");
            String payload = objectMapper.writeValueAsString(eventData);

            context.getLogger().log("Processing webhook event: " + eventType);

            List<WebhookEndpoint> endpoints = dbService.findActiveEndpointsByEvent(clientId, eventType);
            context.getLogger().log("Found " + endpoints.size() + " endpoints for event: " + eventType);

            for (WebhookEndpoint endpoint : endpoints) {
                WebhookDelivery delivery = new WebhookDelivery();
                delivery.setWebhookEndpointId(endpoint.getId());
                delivery.setEventType(eventType);
                delivery.setPayload(payload);
                delivery.setAttemptCount(1);
                delivery.setStatus("PENDING");

                Long deliveryId = dbService.createWebhookDelivery(delivery);

                dbService.insertWebhookDeliveryLog(deliveryId, 1, payload);

                WebhookMessage message = new WebhookMessage(deliveryId, endpoint.getId(), eventType, payload, 1);
                String messageBody = objectMapper.writeValueAsString(message);

                SendMessageRequest sendMessageRequest = new SendMessageRequest()
                        .withQueueUrl(queueUrl)
                        .withMessageBody(messageBody);

                sqs.sendMessage(sendMessageRequest);

                context.getLogger().log("Queued delivery " + deliveryId + " for endpoint " + endpoint.getId());
            }

            return "Published " + endpoints.size() + " webhook deliveries for event: " + eventType;

        } catch (Exception e) {
            context.getLogger().log("Error processing webhook event: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }
}
