package com.payintelli.webhook.models;

import java.time.Instant;
import java.util.Map;

public class WebhookPublisherMessage {
	private String id;
	private String clientId;
	private String eventType;
	private Instant createdAt;
	private Map<String, Object> data;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getClientId() {
		return clientId;
	}

	public void setClientId(String clientId) {
		this.clientId = clientId;
	}

	public String getEventType() {
		return eventType;
	}

	public void setEventType(String eventType) {
		this.eventType = eventType;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(Instant createdAt) {
		this.createdAt = createdAt;
	}

	public Map<String, Object> getData() {
		return data;
	}

	public void setData(Map<String, Object> data) {
		this.data = data;
	}
	
	@Override
	public String toString() {
		return "WebhookPublisherMessage [id=" + id + ", clientId=" + clientId + ", eventType=" + eventType
				+ ", createdAt=" + createdAt + ", data=" + data + "]";
	}
}
