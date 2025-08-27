package com.payintelli.webhook.services;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payintelli.webhook.models.WebhookDelivery;
import com.payintelli.webhook.models.WebhookEndpoint;

public class WebhookDatabaseService {
	private final String dbUrl;
	private final String dbUsername;
	private final String dbPassword;

	private final ObjectMapper objectMapper = new ObjectMapper();

	public WebhookDatabaseService(String dbUrl, String dbUsername, String dbPassword) {
		this.dbUrl = dbUrl;
		this.dbUsername = dbUsername;
		this.dbPassword = dbPassword;
	}

	public Long createWebhookDelivery(WebhookDelivery delivery) throws SQLException {
		String sql = "INSERT INTO webhook_deliveries (webhook_endpoint_id, event_type, payload, attempt_count, status) "
				+ "VALUES (?, ?, ?, ?, ?) RETURNING id";

		try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
				PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setLong(1, delivery.getWebhookEndpointId());
			stmt.setString(2, delivery.getEventType());
			stmt.setString(3, delivery.getPayload());
			stmt.setInt(4, delivery.getAttemptCount());
			stmt.setString(5, delivery.getStatus());

			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					return rs.getLong(1);
				}
				throw new SQLException("Failed to insert delivery");
			}
		}
	}

	public void insertWebhookDeliveryLog(Long deliveryId, int attempt, String requestBody) throws SQLException {
		String sql = "INSERT INTO webhook_delivery_audit_logs "
				+ "(delivery_id, attempt_number, request_body, logged_at, status) "
				+ "VALUES (?, ?, ?, CURRENT_TIMESTAMP, ?)";
		try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
				PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setLong(1, deliveryId);
			stmt.setInt(2, attempt);
			stmt.setString(3, requestBody);
			stmt.setString(4, "PENDING");
			stmt.executeUpdate();
		}
	}

	public void updateDelivery(Long deliveryId, Integer responseStatus, String responseBody, String status,
			Timestamp nextRetryAt, Integer attemptCount, String requestHeaders, String requestBody,
			String responseHeaders) throws Exception {

		String updateSql = "UPDATE webhook_deliveries SET response_status = ?, response_body = ?, status = ?, "
				+ "next_retry_at = ?, attempt_count = ?, delivered_at = ? WHERE id = ?";

		String insertSql = "INSERT INTO webhook_delivery_audit_logs "
				+ "(delivery_id, attempt_number, request_headers, request_body, response_status, response_headers, response_body, logged_at, status) "
				+ "VALUES (?, ?, ?::jsonb, ?, ?, ?::jsonb, ?, CURRENT_TIMESTAMP, ?)";

		try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword)) {
			conn.setAutoCommit(false);

			try (PreparedStatement updateStmt = conn.prepareStatement(updateSql);
					PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {

				updateStmt.setObject(1, responseStatus);
				updateStmt.setString(2, responseBody);
				updateStmt.setString(3, status);
				updateStmt.setTimestamp(4, nextRetryAt);
				updateStmt.setInt(5, attemptCount);
				updateStmt.setTimestamp(6,
						"DELIVERED".equals(status) ? new Timestamp(System.currentTimeMillis()) : null);
				updateStmt.setLong(7, deliveryId);
				updateStmt.executeUpdate();
				insertStmt.setLong(1, deliveryId);
				insertStmt.setInt(2, attemptCount);
				insertStmt.setString(3, requestHeaders);
				insertStmt.setString(4, requestBody);
				insertStmt.setObject(5, responseStatus);
				insertStmt.setString(6, responseHeaders);
				insertStmt.setString(7, responseBody);
				insertStmt.setString(8, status);
				insertStmt.executeUpdate();

				conn.commit();
			} catch (Exception ex) {
				conn.rollback();
				throw ex;
			}
		}
	}

	public WebhookEndpoint findEndpointById(Long endpointId) throws SQLException {
		String sql = "SELECT * FROM webhook_endpoints WHERE id = ? AND is_active = true";
		try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
				PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setLong(1, endpointId);
			try (ResultSet rs = stmt.executeQuery()) {
				if (rs.next()) {
					WebhookEndpoint endpoint = new WebhookEndpoint();
					endpoint.setId(rs.getLong("id"));
					endpoint.setClientId(rs.getString("client_id"));
					endpoint.setUrl(rs.getString("url"));
					endpoint.setEvents(rs.getString("events"));
					endpoint.setSecret(rs.getString("secret"));
					endpoint.setIsActive(rs.getBoolean("is_active"));
					return endpoint;
				}
				return null;
			}
		}
	}

	public List<WebhookEndpoint> findActiveEndpointsByEvent(String clientId, String eventType) throws SQLException {
		List<WebhookEndpoint> endpoints = new ArrayList<>();
		String sql = "SELECT * FROM webhook_endpoints WHERE is_active = true AND client_id = ?";
		try (Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
				PreparedStatement stmt = conn.prepareStatement(sql)) {
			stmt.setString(1, clientId);
			try (ResultSet rs = stmt.executeQuery()) {
				while (rs.next()) {
					WebhookEndpoint endpoint = mapResultSetToEndpoint(rs);
					if (subscribesToEvent(endpoint.getEvents(), eventType)) {
						endpoints.add(endpoint);
					}
				}
			}
		}
		return endpoints;
	}

	private boolean subscribesToEvent(String eventsJson, String eventType) {
		try {
			List<String> events = objectMapper.readValue(eventsJson, new TypeReference<List<String>>() {
			});
			return events.contains(eventType) || events.contains("*");
		} catch (Exception e) {
			System.err.println("Error parsing events JSON: " + e.getMessage());
			return false;
		}
	}

	private WebhookEndpoint mapResultSetToEndpoint(ResultSet rs) throws SQLException {
		WebhookEndpoint endpoint = new WebhookEndpoint();
		endpoint.setId(rs.getLong("id"));
		endpoint.setClientId(rs.getString("client_id"));
		endpoint.setUrl(rs.getString("url"));
		endpoint.setEvents(rs.getString("events"));
		endpoint.setSecret(rs.getString("secret"));
		endpoint.setIsActive(rs.getBoolean("is_active"));
		endpoint.setCreatedAt(rs.getTimestamp("created_at"));
		endpoint.setUpdatedAt(rs.getTimestamp("updated_at"));
		endpoint.setCreatedBy(rs.getString("created_by"));
		endpoint.setNotes(rs.getString("notes"));
		return endpoint;
	}
}
