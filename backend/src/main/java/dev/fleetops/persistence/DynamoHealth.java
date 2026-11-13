package dev.fleetops.persistence;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.*;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Component
public class DynamoHealth implements HealthIndicator {
  private final DynamoDbClient client;
  private final String table;

  public DynamoHealth(DynamoDbClient client, @Value("${fleetops.dynamodb.table}") String table) {
    this.client = client;
    this.table = table;
  }

  public Health health() {
    try {
      client.describeTable(r -> r.tableName(table));
      return Health.up().build();
    } catch (Exception e) {
      return Health.down().build();
    }
  }
}
