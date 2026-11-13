package dev.fleetops.persistence;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoConfiguration {
  @Bean
  DynamoDbClient dynamo(
      @Value("${fleetops.dynamodb.endpoint}") String endpoint,
      @Value("${fleetops.dynamodb.region}") String region) {
    var b = DynamoDbClient.builder().region(Region.of(region));
    if (!endpoint.isBlank()) b.endpointOverride(URI.create(endpoint));
    return b.build();
  }
}
