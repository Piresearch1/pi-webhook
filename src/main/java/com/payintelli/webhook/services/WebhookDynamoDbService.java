package com.payintelli.webhook.services;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payintelli.webhook.models.WebhookEndpoint;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

public class WebhookDynamoDbService {

  private final DynamoDbClient dynamoDb;
  private final String tableName;
  private final ObjectMapper objectMapper;

  public WebhookDynamoDbService(String tableName) {

    this.dynamoDb = DynamoDbClient.builder().build();
    this.tableName = tableName;
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
    this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  }


  public List<WebhookEndpoint> findActiveEndpointsByEvent(String clientId, String eventType)
      throws Exception {
    Map<String, AttributeValue> key =
        Map.of("config_table", AttributeValue.builder().s("WEBHOOK_ENDPOINTS").build());
    GetItemRequest request = GetItemRequest.builder().tableName(tableName).key(key).build();
    Map<String, AttributeValue> item = dynamoDb.getItem(request).item();
    List<WebhookEndpoint> endpoints = List.of();
    if (item != null && item.containsKey("cache_value")) {
      String dataJson = item.get("cache_value").s();
      endpoints = objectMapper.readValue(dataJson, new TypeReference<List<WebhookEndpoint>>() {});
      endpoints = endpoints.stream().filter(e -> e.getClientId().equals(clientId))
          .filter(e -> e.getEvents().contains(eventType)).filter(WebhookEndpoint::getIsActive)
          .toList();
    }
    return endpoints;
  }


  public WebhookEndpoint findEndpointById(Long endpointId) throws Exception {
    Map<String, AttributeValue> key =
        Map.of("config_table", AttributeValue.builder().s("WEBHOOK_ENDPOINTS").build());

    GetItemRequest request = GetItemRequest.builder().tableName(tableName).key(key).build();

    Map<String, AttributeValue> item = dynamoDb.getItem(request).item();

    if (item != null && item.containsKey("cache_value")) {
      String dataJson = item.get("cache_value").s();
      List<WebhookEndpoint> endpoints =
          objectMapper.readValue(dataJson, new TypeReference<List<WebhookEndpoint>>() {});

      return endpoints.stream().filter(e -> e.getId().equals(endpointId)).findFirst().orElse(null);
    }

    return null;
  }

}
