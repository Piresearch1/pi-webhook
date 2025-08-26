package com.payintelli.webhook.services;

import com.payintelli.webhook.models.WebhookEndpoint;
import com.payintelli.webhook.models.WebhookMessage;
import com.payintelli.webhook.utils.WebhookSignatureUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class WebhookHttpService {
    private final HttpClient httpClient;
    
    public WebhookHttpService() {
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }
    
    public HttpResponse<String> sendWebhook(WebhookEndpoint endpoint, WebhookMessage message) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(endpoint.getUrl()))
            .timeout(Duration.ofSeconds(30))
            .header("Content-Type", "application/json")
            .header("User-Agent", "Webhook-Delivery/1.0")
            .header("X-Webhook-Event", message.getEventType())
            .header("X-Webhook-Attempt", String.valueOf(message.getAttemptCount()))
            .POST(HttpRequest.BodyPublishers.ofString(message.getPayload()));
        
        // Add signature header if secret is configured
        if (endpoint.getSecret() != null && !endpoint.getSecret().isEmpty()) {
            String signature = WebhookSignatureUtils.generateSignature(message.getPayload(), endpoint.getSecret());
            requestBuilder.header("X-Webhook-Signature", "sha256=" + signature);
        }
        
        HttpRequest request = requestBuilder.build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}