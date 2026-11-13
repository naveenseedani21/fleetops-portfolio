package dev.fleetops.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fleetops.shared.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Repository
public class Store {
  public record Page<T>(List<T> items, String nextCursor) {}

  public record Change(String key, Object value, long revision, Long expected) {}

  private final DynamoDbClient db;
  private final ObjectMapper json;
  private final String table;

  public Store(
      DynamoDbClient db, ObjectMapper json, @Value("${fleetops.dynamodb.table}") String table) {
    this.db = db;
    this.json = json;
    this.table = table;
  }

  private AttributeValue s(String v) {
    return AttributeValue.fromS(v);
  }

  private Map<String, AttributeValue> key(String tenant, String sk) {
    return Map.of("pk", s("ORG#" + tenant), "sk", s(sk));
  }

  public <T> T get(String tenant, String sk, Class<T> type) {
    var item = db.getItem(r -> r.tableName(table).key(key(tenant, sk)).consistentRead(true)).item();
    if (item.isEmpty()) throw ApiException.missing();
    return decode(item, type);
  }

  private <T> T decode(Map<String, AttributeValue> item, Class<T> type) {
    try {
      return json.readValue(item.get("payload").s(), type);
    } catch (Exception e) {
      throw new IllegalStateException("Stored record cannot be decoded", e);
    }
  }

  public <T> Page<T> list(
      String tenant, String prefix, int limit, String cursor, Class<T> type, boolean reverse) {
    if (limit < 1 || limit > 100) throw ApiException.invalid("Page size must be 1–100");
    var b =
        QueryRequest.builder()
            .tableName(table)
            .consistentRead(true)
            .keyConditionExpression("pk = :p AND begins_with(sk, :s)")
            .expressionAttributeValues(Map.of(":p", s("ORG#" + tenant), ":s", s(prefix)))
            .limit(limit)
            .scanIndexForward(!reverse);
    if (cursor != null && !cursor.isBlank()) {
      String sk;
      try {
        sk = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
      } catch (IllegalArgumentException e) {
        throw ApiException.invalid("Invalid cursor");
      }
      if (!sk.startsWith(prefix) || sk.length() > 512)
        throw ApiException.invalid("Cursor does not match resource");
      b.exclusiveStartKey(key(tenant, sk));
    }
    var result = db.query(b.build());
    String next =
        result.hasLastEvaluatedKey()
            ? Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(
                    result.lastEvaluatedKey().get("sk").s().getBytes(StandardCharsets.UTF_8))
            : null;
    return new Page<>(result.items().stream().map(i -> decode(i, type)).toList(), next);
  }

  public void commit(String tenant, List<Change> changes) {
    var writes = new ArrayList<TransactWriteItem>();
    for (var c : changes) {
      var item = new HashMap<>(key(tenant, c.key()));
      try {
        item.put("payload", s(json.writeValueAsString(c.value())));
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
      item.put("revision", AttributeValue.fromN(Long.toString(c.revision())));
      var b = Put.builder().tableName(table).item(item);
      if (c.expected() == null) b.conditionExpression("attribute_not_exists(pk)");
      else
        b.conditionExpression("revision = :r")
            .expressionAttributeValues(Map.of(":r", AttributeValue.fromN(c.expected().toString())));
      writes.add(TransactWriteItem.builder().put(b.build()).build());
    }
    try {
      db.transactWriteItems(r -> r.transactItems(writes));
    } catch (TransactionCanceledException e) {
      if (e.cancellationReasons().stream().anyMatch(r -> "ConditionalCheckFailed".equals(r.code())))
        throw ApiException.conflict();
      throw e;
    }
  }
}
