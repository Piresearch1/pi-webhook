package com.payintelli.webhook.models;

import java.sql.Timestamp;

public class WebhookEndpoint {
    private Long  id;
    private String clientId;
    private String url;
    private String events; // JSON array string
    private String secret;
    private Boolean isActive;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    private String createdBy;
    private String notes;
    
    public WebhookEndpoint() {}
    
    public WebhookEndpoint(Long  id, String clientId, String url, String events, String secret, Boolean isActive) {
        this.id = id;
        this.clientId = clientId;
        this.url = url;
        this.events = events;
        this.secret = secret;
        this.isActive = isActive;
    }
    
    // Getters and setters
    public Long  getId() { return id; }
    public void setId(Long  id) { this.id = id; }
    
    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    @Override
	public String toString() {
		return "WebhookEndpoint [id=" + id + ", clientId=" + clientId + ", url=" + url + ", events=" + events
				+ ", secret=" + secret + ", isActive=" + isActive + ", createdAt=" + createdAt + ", updatedAt="
				+ updatedAt + ", createdBy=" + createdBy + ", notes=" + notes + "]";
	}

	public String getEvents() { return events; }
    public void setEvents(String events) { this.events = events; }
    
    public String getSecret() { return secret; }
    public void setSecret(String secret) { this.secret = secret; }
    
    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}