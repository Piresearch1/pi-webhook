package com.payintelli.webhook.models;

import java.sql.Timestamp;

public class WebhookDelivery {
  private Long id;
  private Long webhookEndpointId;
  private String eventType;
  private String payload;
  private Integer responseStatus;
  private String responseBody;
  private Integer attemptCount;
  private Timestamp deliveredAt;
  private Timestamp createdAt;
  private Timestamp nextRetryAt;
  private String status; // PENDING, DELIVERED, FAILED, ABANDONED

  public WebhookDelivery() {}

  // Getters and setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
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

  public Integer getResponseStatus() {
    return responseStatus;
  }

  public void setResponseStatus(Integer responseStatus) {
    this.responseStatus = responseStatus;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public void setResponseBody(String responseBody) {
    this.responseBody = responseBody;
  }

  public Integer getAttemptCount() {
    return attemptCount;
  }

  public void setAttemptCount(Integer attemptCount) {
    this.attemptCount = attemptCount;
  }

  public Timestamp getDeliveredAt() {
    return deliveredAt;
  }

  public void setDeliveredAt(Timestamp deliveredAt) {
    this.deliveredAt = deliveredAt;
  }

  public Timestamp getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Timestamp createdAt) {
    this.createdAt = createdAt;
  }

  public Timestamp getNextRetryAt() {
    return nextRetryAt;
  }

  public void setNextRetryAt(Timestamp nextRetryAt) {
    this.nextRetryAt = nextRetryAt;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
