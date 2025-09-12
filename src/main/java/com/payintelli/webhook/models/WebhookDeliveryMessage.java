package com.payintelli.webhook.models;

public class WebhookDeliveryMessage {
  private Long deliveryId;
  private Long webhookEndpointId;
  private String eventType;
  private String payload;
  private Integer attemptCount;

  public WebhookDeliveryMessage() {}

  public WebhookDeliveryMessage(Long deliveryId, Long webhookEndpointId, String eventType,
      String payload, Integer attemptCount) {
    this.deliveryId = deliveryId;
    this.webhookEndpointId = webhookEndpointId;
    this.eventType = eventType;
    this.payload = payload;
    this.attemptCount = attemptCount;
  }

  // Getters and setters
  public Long getDeliveryId() {
    return deliveryId;
  }

  public void setDeliveryId(Long deliveryId) {
    this.deliveryId = deliveryId;
  }

  public Long getWebhookEndpointId() {
    return webhookEndpointId;
  }

  public void setWebhookEndpointId(Long webhookEndpointId) {
    this.webhookEndpointId = webhookEndpointId;
  }

  public String getEventType() {
    return eventType;
  }

  public void setEventType(String eventType) {
    this.eventType = eventType;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public Integer getAttemptCount() {
    return attemptCount;
  }

  public void setAttemptCount(Integer attemptCount) {
    this.attemptCount = attemptCount;
  }

  @Override
  public String toString() {
    return "WebhookMessage [deliveryId=" + deliveryId + ", webhookEndpointId=" + webhookEndpointId
        + ", eventType=" + eventType + ", payload=" + payload + ", attemptCount=" + attemptCount
        + "]";
  }
}
