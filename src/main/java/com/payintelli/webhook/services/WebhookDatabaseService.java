package com.payintelli.webhook.services;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import com.payintelli.webhook.models.WebhookDelivery;

public class WebhookDatabaseService {
  private final String dbUrl;
  private final String dbUsername;
  private final String dbPassword;

  public WebhookDatabaseService(String dbUrl, String dbUsername, String dbPassword) {
    this.dbUrl = dbUrl;
    this.dbUsername = dbUsername;
    this.dbPassword = dbPassword;
  }

  public Long createWebhookDelivery(WebhookDelivery delivery) throws SQLException {
    String sql =
        "INSERT INTO webhook_deliveries (webhook_endpoint_id, event_type, payload, attempt_count, status) "
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

  public void insertWebhookDeliveryLog(Long deliveryId, int attempt, String requestBody)
      throws SQLException {
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

  public void updateDelivery(Long deliveryId, Integer responseStatus, String responseBody,
      String status, Timestamp nextRetryAt, Integer attemptCount, String requestHeaders,
      String requestBody, String responseHeaders) throws Exception {

    String updateSql =
        "UPDATE webhook_deliveries SET response_status = ?, response_body = ?, status = ?, "
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
}
