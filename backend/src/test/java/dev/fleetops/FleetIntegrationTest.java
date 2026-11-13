package dev.fleetops;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@SpringBootTest
@AutoConfigureMockMvc
class FleetIntegrationTest {
  static GenericContainer<?> container;

  static String endpoint() {
    String external = System.getenv("TEST_DYNAMODB_ENDPOINT");
    if (external != null) return external;
    container =
        new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.6.1"))
            .withExposedPorts(8000)
            .withCommand("-jar DynamoDBLocal.jar -inMemory -sharedDb");
    container.start();
    return "http://" + container.getHost() + ":" + container.getMappedPort(8000);
  }

  static final String ENDPOINT = endpoint();
  static final String TABLE = "fleetops-test-" + UUID.randomUUID();

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry r) {
    r.add("fleetops.dynamodb.endpoint", () -> ENDPOINT);
    r.add("fleetops.dynamodb.table", () -> TABLE);
  }

  @Autowired MockMvc mvc;
  @Autowired ObjectMapper json;
  @Autowired DynamoDbClient db;

  @BeforeEach
  void table() {
    try {
      db.createTable(
          b ->
              b.tableName(TABLE)
                  .billingMode(BillingMode.PAY_PER_REQUEST)
                  .attributeDefinitions(
                      AttributeDefinition.builder()
                          .attributeName("pk")
                          .attributeType(ScalarAttributeType.S)
                          .build(),
                      AttributeDefinition.builder()
                          .attributeName("sk")
                          .attributeType(ScalarAttributeType.S)
                          .build())
                  .keySchema(
                      KeySchemaElement.builder().attributeName("pk").keyType(KeyType.HASH).build(),
                      KeySchemaElement.builder()
                          .attributeName("sk")
                          .keyType(KeyType.RANGE)
                          .build()));
    } catch (ResourceInUseException ignored) {
    }
  }

  @AfterAll
  static void stop() {
    if (container != null) container.stop();
  }

  RequestPostProcessor role(String org, String role) {
    return jwt().jwt(j -> j.subject("test-user").claim("organizations", Map.of(org, role)));
  }

  String base(String org) {
    return "/api/v1/organizations/" + org;
  }

  String register(String org, String serial) throws Exception {
    return mvc.perform(
            post(base(org) + "/devices")
                .with(role(org, "ADMIN"))
                .contentType("application/json")
                .content(
                    json.writeValueAsString(
                        Map.of(
                            "name",
                            "Synthetic Mac",
                            "type",
                            "MAC",
                            "model",
                            "MacBook",
                            "os",
                            "macOS",
                            "osVersion",
                            "15.10",
                            "serial",
                            serial))))
        .andExpect(status().isCreated())
        .andReturn()
        .getResponse()
        .getContentAsString();
  }

  @Test
  void authenticationAndIsolation() throws Exception {
    String org = UUID.randomUUID().toString();
    JsonNode d = json.readTree(register(org, "SYN-001"));
    String path = base(org) + "/devices/" + d.get("id").asText();
    mvc.perform(get(path)).andExpect(status().isUnauthorized());
    mvc.perform(get(path).with(role("other", "ADMIN"))).andExpect(status().isForbidden());
    mvc.perform(
            get(path)
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.claim(
                                    "realm_access", Map.of("roles", List.of("PLATFORM_ADMIN"))))))
        .andExpect(status().isForbidden());
    mvc.perform(get(path).with(role(org, "VIEWER"))).andExpect(status().isOk());
    mvc.perform(
            post(path + "/retire")
                .with(role(org, "VIEWER"))
                .contentType("application/json")
                .content("{\"revision\":1}"))
        .andExpect(status().isForbidden());
    mvc.perform(get(path).with(role(org, "INGESTOR"))).andExpect(status().isForbidden());
  }

  @Test
  void paginationDuplicateAndOptimisticLock() throws Exception {
    String org = UUID.randomUUID().toString();
    JsonNode d = json.readTree(register(org, "SYN-001"));
    register(org, "SYN-002");
    mvc.perform(
            post(base(org) + "/devices")
                .with(role(org, "ADMIN"))
                .contentType("application/json")
                .content(
                    "{\"name\":\"Duplicate\",\"type\":\"MAC\",\"model\":\"Mac\",\"os\":\"macOS\",\"serial\":\"syn-001\"}"))
        .andExpect(status().isConflict());
    var first =
        json.readTree(
            mvc.perform(get(base(org) + "/devices?limit=1").with(role(org, "VIEWER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString());
    var second =
        json.readTree(
            mvc.perform(
                    get(base(org) + "/devices")
                        .param("limit", "1")
                        .param("cursor", first.get("nextCursor").asText())
                        .with(role(org, "VIEWER")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    assertNotEquals(first.get("items").get(0).get("id"), second.get("items").get(0).get("id"));
    String path = base(org) + "/devices/" + d.get("id").asText();
    mvc.perform(
            patch(path)
                .with(role(org, "ADMIN"))
                .contentType("application/json")
                .content("{\"revision\":1,\"name\":\"Renamed\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(2));
    mvc.perform(
            patch(path)
                .with(role(org, "ADMIN"))
                .contentType("application/json")
                .content("{\"revision\":1,\"name\":\"Stale\"}"))
        .andExpect(status().isConflict());
    mvc.perform(get(path).with(role(org, "ADMIN"))).andExpect(jsonPath("$.name").value("Renamed"));
    mvc.perform(get(base(org) + "/audit-events").with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.items.length()").value(3));
  }

  @Test
  void policyHistoryAndReports() throws Exception {
    String org = UUID.randomUUID().toString();
    JsonNode d = json.readTree(register(org, "SYN-001"));
    String path = base(org) + "/devices/" + d.get("id").asText();
    String policy =
        "{\"version\":0,\"name\":\"Encryption\",\"targetTypes\":[\"MAC\"],\"rules\":[{\"type\":\"ENCRYPTION\",\"value\":\"true\"}],\"severity\":\"HIGH\",\"enabled\":true}";
    JsonNode p =
        json.readTree(
            mvc.perform(
                    post(base(org) + "/policies")
                        .with(role(org, "ADMIN"))
                        .contentType("application/json")
                        .content(policy))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
    mvc.perform(post(path + "/evaluations").with(role(org, "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UNKNOWN"));
    String report =
        "{\"reportId\":\"r1\",\"sequence\":1,\"reportedAt\":\"2026-01-01T00:00:00Z\",\"osVersion\":\"15.10\",\"encryption\":false}";
    mvc.perform(
            post(path + "/reports")
                .with(role(org, "INGESTOR"))
                .contentType("application/json")
                .content(report))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compliance").value("NONCOMPLIANT"))
        .andExpect(jsonPath("$.revision").value(3));
    mvc.perform(
            post(path + "/reports")
                .with(role(org, "INGESTOR"))
                .contentType("application/json")
                .content(report))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.revision").value(3));
    mvc.perform(
            post(path + "/reports")
                .with(role(org, "INGESTOR"))
                .contentType("application/json")
                .content(report.replace("false", "true")))
        .andExpect(status().isConflict());
    mvc.perform(
            put(base(org) + "/policies/" + p.get("id").asText())
                .with(role(org, "ADMIN"))
                .contentType("application/json")
                .content(
                    policy
                        .replace("\"version\":0", "\"version\":1")
                        .replace("\"enabled\":true", "\"enabled\":false")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version").value(2));
    mvc.perform(
            get(base(org) + "/policies/" + p.get("id").asText() + "/history")
                .with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.items.length()").value(2));
    mvc.perform(get(path + "/evaluations").with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].policies[0].policyVersion").value(1));
  }

  @Test
  void invalidPayloadAndCursor() throws Exception {
    String org = UUID.randomUUID().toString();
    mvc.perform(
            post(base(org) + "/devices")
                .with(role(org, "ADMIN"))
                .contentType("application/json")
                .content("{}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(base(org) + "/devices?cursor=not-base64!").with(role(org, "ADMIN")))
        .andExpect(status().isBadRequest());
  }
}
