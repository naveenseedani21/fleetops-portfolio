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
  void dashboardCountsPersistedTenantDevices() throws Exception {
    String org = UUID.randomUUID().toString();
    register(org, "SUMMARY-001");
    mvc.perform(get(base(org) + "/dashboard/summary").with(role(org, "VIEWER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.unknown").value(1))
        .andExpect(jsonPath("$.stale").value(1))
        .andExpect(jsonPath("$.compliancePercent").isEmpty());
    mvc.perform(get(base(org) + "/dashboard/summary").with(role("other", "ADMIN")))
        .andExpect(status().isForbidden());
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

  @Test
  void policyAndAuditTenantBoundariesAndMachinePermissions() throws Exception {
    String org = UUID.randomUUID().toString();
    String path = base(org);
    String policy =
        "{\"version\":0,\"name\":\"Baseline\",\"targetTypes\":[\"MAC\"],\"rules\":[{\"type\":\"MIN_OS\",\"value\":\"15.2\"}],\"severity\":\"HIGH\",\"enabled\":true}";
    for (var principal :
        List.of(role("other", "ADMIN"), role(org, "VIEWER"), role(org, "INGESTOR"))) {
      mvc.perform(
              post(path + "/policies")
                  .with(principal)
                  .contentType("application/json")
                  .content(policy))
          .andExpect(status().isForbidden());
      mvc.perform(
              put(path + "/policies/nonexistent")
                  .with(principal)
                  .contentType("application/json")
                  .content(policy))
          .andExpect(status().isForbidden());
    }
    for (var principal : List.of(role("other", "ADMIN"), role(org, "INGESTOR"))) {
      mvc.perform(get(path + "/audit-events").with(principal)).andExpect(status().isForbidden());
      mvc.perform(get(path + "/policies").with(principal)).andExpect(status().isForbidden());
    }
    JsonNode device = json.readTree(register(org, "BOUNDARY-001"));
    String devicePath = path + "/devices/" + device.get("id").asText();
    mvc.perform(post(devicePath + "/evaluations").with(role(org, "INGESTOR")))
        .andExpect(status().isForbidden());
    mvc.perform(
            patch(devicePath)
                .with(role(org, "INGESTOR"))
                .contentType("application/json")
                .content("{\"revision\":1,\"name\":\"Unauthorized\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void minimumOsRecoveryRejectsStaleReportsWithoutDuplicateEvidence() throws Exception {
    String org = UUID.randomUUID().toString();
    JsonNode device = json.readTree(register(org, "RECOVERY-001"));
    String path = base(org) + "/devices/" + device.get("id").asText();
    String policy =
        "{\"version\":0,\"name\":\"OS baseline\",\"targetTypes\":[\"MAC\"],\"rules\":[{\"type\":\"MIN_OS\",\"value\":\"15.2\"}],\"severity\":\"HIGH\",\"enabled\":true}";
    mvc.perform(
            post(base(org) + "/policies")
                .with(role(org, "ADMIN"))
                .contentType("application/json")
                .content(policy))
        .andExpect(status().isCreated());
    String oldReport =
        "{\"reportId\":\"old\",\"sequence\":1,\"reportedAt\":\"2026-01-01T00:00:00Z\",\"osVersion\":\"14.10\"}";
    String newReport =
        "{\"reportId\":\"new\",\"sequence\":2,\"reportedAt\":\"2026-01-02T00:00:00Z\",\"osVersion\":\"15.2\"}";
    mvc.perform(
            post(path + "/reports")
                .with(role(org, "INGESTOR"))
                .contentType("application/json")
                .content(oldReport))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.compliance").value("NONCOMPLIANT"));
    mvc.perform(get(path + "/evaluations").with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.items[0].policies[0].rules[0].rule").value("MIN_OS"))
        .andExpect(jsonPath("$.items[0].policies[0].rules[0].reason").value("Requirement not met"));
    mvc.perform(get(base(org) + "/audit-events").with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.items.length()").value(4));
    for (int repeat = 0; repeat < 2; repeat++) {
      mvc.perform(
              post(path + "/reports")
                  .with(role(org, "INGESTOR"))
                  .contentType("application/json")
                  .content(newReport))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.compliance").value("COMPLIANT"))
          .andExpect(jsonPath("$.revision").value(3));
    }
    mvc.perform(
            post(path + "/reports")
                .with(role(org, "INGESTOR"))
                .contentType("application/json")
                .content(oldReport))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sequence").value(2));
    mvc.perform(
            post(path + "/reports")
                .with(role(org, "INGESTOR"))
                .contentType("application/json")
                .content(oldReport.replace("old", "unseen-stale")))
        .andExpect(status().isConflict());
    mvc.perform(get(path).with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.sequence").value(2))
        .andExpect(jsonPath("$.compliance").value("COMPLIANT"));
    mvc.perform(get(path + "/evaluations").with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].status").value("COMPLIANT"))
        .andExpect(jsonPath("$.items[1].status").value("NONCOMPLIANT"))
        .andExpect(jsonPath("$.items[1].policies[0].policyVersion").value(1));
    mvc.perform(get(base(org) + "/audit-events").with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.items.length()").value(6));
  }

  @Test
  void retirementPreservesComplianceAndRejectsFurtherMutation() throws Exception {
    String org = UUID.randomUUID().toString();
    JsonNode device = json.readTree(register(org, "RETIRE-001"));
    String path = base(org) + "/devices/" + device.get("id").asText();
    mvc.perform(
            patch(path)
                .with(role(org, "ADMIN"))
                .contentType("application/json")
                .content("{\"revision\":1,\"name\":\"Assigned\",\"employee\":\"Employee 1\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employee").value("Employee 1"));
    mvc.perform(
            patch(path)
                .with(role(org, "ADMIN"))
                .contentType("application/json")
                .content("{\"revision\":2,\"name\":\"Unassigned\",\"employee\":null}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employee").isEmpty());
    mvc.perform(
            post(path + "/retire")
                .with(role(org, "ADMIN"))
                .contentType("application/json")
                .content("{\"revision\":3}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("RETIRED"))
        .andExpect(jsonPath("$.compliance").value("UNKNOWN"));
    mvc.perform(post(path + "/evaluations").with(role(org, "ADMIN")))
        .andExpect(status().isBadRequest());
    mvc.perform(
            patch(path)
                .with(role(org, "ADMIN"))
                .contentType("application/json")
                .content("{\"revision\":4,\"name\":\"Resurrected\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(
            post(path + "/reports")
                .with(role(org, "INGESTOR"))
                .contentType("application/json")
                .content(
                    "{\"reportId\":\"after-retire\",\"sequence\":1,\"reportedAt\":\"2026-01-01T00:00:00Z\"}"))
        .andExpect(status().isBadRequest());
    mvc.perform(get(base(org) + "/audit-events").with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.items.length()").value(4));
  }

  @Test
  void conflictingTransactionCannotLeavePartialAudit() throws Exception {
    String org = UUID.randomUUID().toString();
    JsonNode device = json.readTree(register(org, "ATOMIC-001"));
    var store = new dev.fleetops.persistence.Store(db, json, TABLE);
    var before =
        store.get(org, "DEVICE#" + device.get("id").asText(), dev.fleetops.devices.Device.class);
    var audit =
        dev.fleetops.audit.Audit.change(
            org, "test-user", "MUST_NOT_COMMIT", before.id(), java.time.Instant.now());
    assertThrows(
        dev.fleetops.shared.ApiException.class,
        () ->
            store.commit(
                org,
                List.of(
                    new dev.fleetops.persistence.Store.Change(
                        "DEVICE#" + before.id(), before, 2, 999L),
                    audit)));
    assertEquals(
        1, store.get(org, "DEVICE#" + before.id(), dev.fleetops.devices.Device.class).revision());
    var events = store.list(org, "AUDIT#", 25, null, dev.fleetops.audit.Audit.class, false);
    assertEquals(1, events.items().size());
    assertEquals("DEVICE_REGISTERED", events.items().get(0).action());
  }

  @Test
  void adminProvisioningAndScopedMachinesAreEnforcedWithoutSideEffects() throws Exception {
    String org = UUID.randomUUID().toString();
    String registration =
        "{\"name\":\"Unauthorized\",\"type\":\"MAC\",\"model\":\"Mac\",\"os\":\"macOS\",\"serial\":\"DENIED-001\"}";
    for (var principal :
        List.of(role(org, "VIEWER"), role(org, "INGESTOR"), role("other", "ADMIN"))) {
      mvc.perform(
              post(base(org) + "/devices")
                  .with(principal)
                  .contentType("application/json")
                  .content(registration))
          .andExpect(status().isForbidden());
    }
    mvc.perform(get(base(org) + "/devices").with(role(org, "ADMIN")))
        .andExpect(jsonPath("$.items.length()").value(0));
    mvc.perform(get(base(org) + "/audit-events").with(role(org, "ADMIN")))
        .andExpect(jsonPath("$.items.length()").value(0));
    JsonNode device = json.readTree(register(org, "ALLOWED-001"));
    String id = device.get("id").asText();
    String path = base(org) + "/devices/" + id + "/reports";
    String report =
        "{\"reportId\":\"scoped\",\"sequence\":1,\"reportedAt\":\"2026-01-01T00:00:00Z\"}";
    for (var principal :
        List.of(
            role("other", "INGESTOR"),
            role(org, "VIEWER"),
            jwt()
                .jwt(
                    j ->
                        j.subject("machine")
                            .claim("organizations", Map.of(org, "INGESTOR"))
                            .claim("device_scopes", Map.of(org, List.of("another-device")))),
            jwt()
                .jwt(
                    j ->
                        j.subject("machine")
                            .claim("organizations", Map.of(org, "INGESTOR"))
                            .claim("device_scopes", Map.of(org, List.of()))),
            jwt()
                .jwt(
                    j ->
                        j.subject("machine")
                            .claim("organizations", Map.of(org, "INGESTOR"))
                            .claim("device_scopes", "malformed")))) {
      mvc.perform(post(path).with(principal).contentType("application/json").content(report))
          .andExpect(status().isForbidden());
    }
    mvc.perform(
            post(base(org) + "/devices/unknown/reports")
                .with(role(org, "INGESTOR"))
                .contentType("application/json")
                .content(report))
        .andExpect(status().isNotFound());
    mvc.perform(get(base(org) + "/devices/" + id).with(role(org, "ADMIN")))
        .andExpect(jsonPath("$.revision").value(1));
    mvc.perform(get(base(org) + "/audit-events").with(role(org, "ADMIN")))
        .andExpect(jsonPath("$.items.length()").value(1));
    mvc.perform(
            post(path)
                .with(
                    jwt()
                        .jwt(
                            j ->
                                j.subject("machine")
                                    .claim("organizations", Map.of(org, "INGESTOR"))
                                    .claim("device_scopes", Map.of(org, List.of(id)))))
                .contentType("application/json")
                .content(report))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ACTIVE"));
  }

  @Test
  void evaluationFailureAndRecoveryDoNotChangeLifecycle() throws Exception {
    for (String lifecycle : List.of("REGISTERED", "ACTIVE", "INACTIVE")) {
      String org = UUID.randomUUID().toString();
      JsonNode registered = json.readTree(register(org, "STATE-001"));
      String id = registered.get("id").asText();
      var stored = (com.fasterxml.jackson.databind.node.ObjectNode) registered;
      stored.put("status", lifecycle);
      var device = json.treeToValue(stored, dev.fleetops.devices.Device.class);
      var store = new dev.fleetops.persistence.Store(db, json, TABLE);
      store.commit(
          org, List.of(new dev.fleetops.persistence.Store.Change("DEVICE#" + id, device, 1, 1L)));
      String policy =
          "{\"version\":0,\"name\":\"OS baseline\",\"targetTypes\":[\"MAC\"],\"rules\":[{\"type\":\"MIN_OS\",\"value\":\"16\"}],\"severity\":\"HIGH\",\"enabled\":true}";
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
      String path = base(org) + "/devices/" + id;
      mvc.perform(post(path + "/evaluations").with(role(org, "ADMIN")))
          .andExpect(jsonPath("$.status").value("NONCOMPLIANT"));
      mvc.perform(get(path).with(role(org, "VIEWER")))
          .andExpect(jsonPath("$.status").value(lifecycle))
          .andExpect(jsonPath("$.compliance").value("NONCOMPLIANT"));
      mvc.perform(
              put(base(org) + "/policies/" + p.get("id").asText())
                  .with(role(org, "ADMIN"))
                  .contentType("application/json")
                  .content(
                      policy.replace("\"version\":0", "\"version\":1").replace("\"16\"", "\"15\"")))
          .andExpect(status().isOk());
      mvc.perform(post(path + "/evaluations").with(role(org, "ADMIN")))
          .andExpect(jsonPath("$.status").value("COMPLIANT"));
      mvc.perform(get(path).with(role(org, "VIEWER")))
          .andExpect(jsonPath("$.status").value(lifecycle))
          .andExpect(jsonPath("$.compliance").value("COMPLIANT"));
    }
  }

  @Test
  void legacyLifecycleReadsAreConservativeAndNeverRewriteStorage() throws Exception {
    String org = UUID.randomUUID().toString();
    var store = new dev.fleetops.persistence.Store(db, json, TABLE);
    for (int sequence : List.of(0, 1)) {
      var raw =
          (com.fasterxml.jackson.databind.node.ObjectNode)
              json.readTree(register(org, "LEGACY-00" + sequence));
      String id = raw.get("id").asText();
      raw.put("status", "NONCOMPLIANT");
      raw.put("compliance", "NONCOMPLIANT");
      raw.put("sequence", sequence);
      if (sequence > 0) {
        raw.put("lastCheckIn", "2026-01-01T00:00:00Z");
        raw.put("lastReportId", "legacy");
        raw.put("lastReportHash", "legacy-hash");
      }
      var legacy = json.treeToValue(raw, dev.fleetops.devices.Device.class);
      store.commit(
          org, List.of(new dev.fleetops.persistence.Store.Change("DEVICE#" + id, legacy, 1, 1L)));
      mvc.perform(get(base(org) + "/devices/" + id).with(role(org, "VIEWER")))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value(sequence == 0 ? "REGISTERED" : "NONCOMPLIANT"))
          .andExpect(jsonPath("$.compliance").value("NONCOMPLIANT"));
      assertEquals(legacy, store.get(org, "DEVICE#" + id, dev.fleetops.devices.Device.class));
    }
    mvc.perform(get(base(org) + "/dashboard/summary").with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.active").value(0))
        .andExpect(jsonPath("$.noncompliant").value(2))
        .andExpect(jsonPath("$.lifecycleReviewRequired").value(1));
    mvc.perform(get(base(org) + "/devices").with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void acceptedReportIdsSurviveInterveningReportsAndRemainScoped() throws Exception {
    String org = UUID.randomUUID().toString();
    var device = json.readTree(register(org, "LEDGER-001"));
    String path = base(org) + "/devices/" + device.get("id").asText();
    String a =
        "{\"reportId\":\"report-a\",\"sequence\":1,\"reportedAt\":\"2026-01-01T00:00:00Z\",\"encryption\":false}";
    String b =
        "{\"reportId\":\"report-b\",\"sequence\":2,\"reportedAt\":\"2026-01-02T00:00:00Z\",\"encryption\":true}";
    for (String report : List.of(a, b)) {
      mvc.perform(
              post(path + "/reports")
                  .with(role(org, "INGESTOR"))
                  .contentType("application/json")
                  .content(report))
          .andExpect(status().isOk());
    }
    String before =
        mvc.perform(get(path).with(role(org, "VIEWER")))
            .andReturn()
            .getResponse()
            .getContentAsString();
    mvc.perform(
            post(path + "/reports")
                .with(role(org, "INGESTOR"))
                .contentType("application/json")
                .content(a))
        .andExpect(status().isOk())
        .andExpect(content().json(before));
    for (String changed :
        List.of(
            a.replace("false", "true"),
            a.replace("\"sequence\":1", "\"sequence\":3").replace("2026-01-01", "2026-01-03"))) {
      mvc.perform(
              post(path + "/reports")
                  .with(role(org, "INGESTOR"))
                  .contentType("application/json")
                  .content(changed))
          .andExpect(status().isConflict());
    }
    mvc.perform(get(path).with(role(org, "VIEWER"))).andExpect(content().json(before));
    mvc.perform(get(path + "/evaluations").with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.items.length()").value(2));
    mvc.perform(get(base(org) + "/audit-events").with(role(org, "VIEWER")))
        .andExpect(jsonPath("$.items.length()").value(5));
    var second = json.readTree(register(org, "LEDGER-002"));
    mvc.perform(
            post(base(org) + "/devices/" + second.get("id").asText() + "/reports")
                .with(role(org, "INGESTOR"))
                .contentType("application/json")
                .content(a))
        .andExpect(status().isOk());
    String other = UUID.randomUUID().toString();
    var third = json.readTree(register(other, "LEDGER-001"));
    mvc.perform(
            post(base(other) + "/devices/" + third.get("id").asText() + "/reports")
                .with(role(other, "INGESTOR"))
                .contentType("application/json")
                .content(a))
        .andExpect(status().isOk());
    mvc.perform(
            post(path + "/reports")
                .with(role(other, "INGESTOR"))
                .contentType("application/json")
                .content(a))
        .andExpect(status().isForbidden());
  }

  @Test
  void historyOrdersMixedPrecisionAndEqualInstantsAcrossPages() throws Exception {
    String org = UUID.randomUUID().toString();
    var device = json.readTree(register(org, "ORDER-001"));
    String id = device.get("id").asText();
    var store = new dev.fleetops.persistence.Store(db, json, TABLE);
    String auditOrg = UUID.randomUUID().toString();
    List<String> times =
        List.of(
            "2026-01-01T00:00:00Z",
            "2026-01-01T00:00:00.123Z",
            "2026-01-01T00:00:00.123456Z",
            "2026-01-01T00:00:00.123456789Z",
            "2026-01-01T00:00:01Z",
            "2026-01-01T00:00:00.123Z");
    for (int i : List.of(2, 0, 5, 4, 1, 3)) {
      var instant = java.time.Instant.parse(times.get(i));
      var audit =
          new dev.fleetops.audit.Audit(
              "event-" + i, auditOrg, "fixture", "FIXED_EVENT", id, instant, "fixture", "SUCCESS");
      store.commit(
          auditOrg,
          List.of(
              new dev.fleetops.persistence.Store.Change(
                  "AUDIT#" + times.get(i) + "#" + audit.id(), audit, 1, null)));
      var evidence =
          new dev.fleetops.devices.DeviceService.Evidence(
              "event-" + i, org, id, instant, "UNKNOWN", List.of());
      store.commit(
          org,
          List.of(
              new dev.fleetops.persistence.Store.Change(
                  "EVAL#" + id + "#" + times.get(i) + "#" + evidence.id(), evidence, 1, null)));
    }
    List<String> actual = new ArrayList<>();
    String cursor = null;
    do {
      var request =
          get(base(org) + "/devices/" + id + "/evaluations")
              .with(role(org, "VIEWER"))
              .param("limit", "2");
      if (cursor != null) request.param("cursor", cursor);
      var page =
          json.readTree(
              mvc.perform(request)
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString());
      page.get("items").forEach(item -> actual.add(item.get("id").asText()));
      cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
      assertTrue(actual.size() <= 6, "Pagination repeated entries");
    } while (cursor != null);
    assertEquals(List.of("event-4", "event-3", "event-2", "event-5", "event-1", "event-0"), actual);
    actual.clear();
    cursor = null;
    do {
      var request =
          get(base(auditOrg) + "/audit-events").with(role(auditOrg, "VIEWER")).param("limit", "2");
      if (cursor != null) request.param("cursor", cursor);
      var page =
          json.readTree(
              mvc.perform(request)
                  .andExpect(status().isOk())
                  .andReturn()
                  .getResponse()
                  .getContentAsString());
      page.get("items").forEach(item -> actual.add(item.get("id").asText()));
      cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asText();
      assertTrue(actual.size() <= 6);
    } while (cursor != null);
    assertEquals(List.of("event-4", "event-3", "event-2", "event-5", "event-1", "event-0"), actual);
  }

  private org.hamcrest.Matcher<String> startsWithBearer() {
    return org.hamcrest.Matchers.startsWith("Bearer");
  }

  @Test
  void invalidQueryContractsPreserveStatusAndSafeProblemDetails() throws Exception {
    String org = UUID.randomUUID().toString();
    for (String limit : List.of("0", "101", "not-a-number", "999999999999999999999")) {
      mvc.perform(get(base(org) + "/devices").param("limit", limit).with(role(org, "VIEWER")))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
          .andExpect(jsonPath("$.status").value(400))
          .andExpect(jsonPath("$.detail").isString());
    }
    mvc.perform(
            get(base(org) + "/devices").param("cursor", "not-base64!").with(role(org, "VIEWER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Invalid cursor"));
    mvc.perform(get(base(org) + "/devices"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string("WWW-Authenticate", startsWithBearer()));
    mvc.perform(get(base(org) + "/devices").with(role("other", "VIEWER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.status").value(403))
        .andExpect(jsonPath("$.detail").value("Tenant permission denied"));
  }

  @Test
  void chronologicalReaderCrossesDatabasePagesAndAcceptsLegacyCursor() throws Exception {
    String org = UUID.randomUUID().toString();
    var store = new dev.fleetops.persistence.Store(db, json, TABLE);
    var changes = new ArrayList<dev.fleetops.persistence.Store.Change>();
    for (int i = 104; i >= 0; i--) {
      var time = java.time.Instant.parse("2026-01-01T00:00:00Z").plusSeconds(i);
      var audit =
          new dev.fleetops.audit.Audit(
              "id-" + i, org, "fixture", "ORDER", "fixture", time, "fixture", "SUCCESS");
      String stamp =
          i % 2 == 0 ? time.toString() : dev.fleetops.persistence.HistoryKeys.timestamp(time);
      changes.add(
          new dev.fleetops.persistence.Store.Change(
              "AUDIT#" + stamp + "#" + audit.id(), audit, 1, null));
      if (changes.size() == 25) {
        store.commit(org, changes);
        changes.clear();
      }
    }
    if (!changes.isEmpty()) store.commit(org, changes);
    var first =
        json.readTree(
            mvc.perform(get(base(org) + "/audit-events?limit=100").with(role(org, "VIEWER")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    var second =
        json.readTree(
            mvc.perform(
                    get(base(org) + "/audit-events")
                        .param("limit", "100")
                        .param("cursor", first.get("nextCursor").asText())
                        .with(role(org, "VIEWER")))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
    List<String> actual = new ArrayList<>();
    first.get("items").forEach(a -> actual.add(a.get("id").asText()));
    second.get("items").forEach(a -> actual.add(a.get("id").asText()));
    assertEquals(
        java.util.stream.IntStream.iterate(104, i -> i >= 0, i -> i - 1)
            .mapToObj(i -> "id-" + i)
            .toList(),
        actual);
    assertTrue(second.get("nextCursor").isNull());
    String legacy =
        Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(
                "AUDIT#2026-01-01T00:00:02Z#id-2"
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    mvc.perform(get(base(org) + "/audit-events").param("cursor", legacy).with(role(org, "VIEWER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].id").value("id-1"))
        .andExpect(jsonPath("$.items[1].id").value("id-0"));
    mvc.perform(
            get(base(org) + "/audit-events")
                .param(
                    "cursor",
                    Base64.getUrlEncoder()
                        .encodeToString("EVAL#other#2026-01-01T00:00:02Z#id-2".getBytes()))
                .with(role(org, "VIEWER")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").value("Invalid history cursor"));
    mvc.perform(get(base(org) + "/audit-events").with(role("other", "VIEWER")))
        .andExpect(status().isForbidden());
  }
}
