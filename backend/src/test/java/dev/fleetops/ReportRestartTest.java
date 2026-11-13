package dev.fleetops;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

class ReportRestartTest {
  @TempDir Path temporary;
  final ObjectMapper json = new ObjectMapper();
  final String org = "restart-" + UUID.randomUUID();
  final String table = "fleetops-restart-test-" + UUID.randomUUID();
  final String issuer = "https://restart.example.test";

  String token(RSAKey key, String role) throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .issuer(issuer)
            .subject("restart-" + role)
            .audience("fleetops-api")
            .expirationTime(Date.from(Instant.now().plusSeconds(600)))
            .claim("organizations", Map.of(org, role))
            .build();
    var signed =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
    signed.sign(new RSASSASigner(key));
    return signed.serialize();
  }

  final class Backend implements AutoCloseable {
    final Process process;
    final int port;

    Backend(String endpoint, int identityPort, String label) throws Exception {
      try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
        port = socket.getLocalPort();
      }
      Path output = temporary.resolve(label + ".log");
      var command =
          List.of(
              Path.of(System.getProperty("java.home"), "bin", "java").toString(),
              "-Xmx256m",
              "-cp",
              System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")),
              "dev.fleetops.FleetOpsApplication",
              "--server.address=127.0.0.1",
              "--server.port=" + port,
              "--fleetops.dynamodb.endpoint=" + endpoint,
              "--fleetops.dynamodb.table=" + table,
              "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + issuer,
              "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://127.0.0.1:"
                  + identityPort
                  + "/jwks");
      process =
          new ProcessBuilder(command)
              .redirectErrorStream(true)
              .redirectOutput(output.toFile())
              .start();
      try (var client = HttpClient.newHttpClient()) {
        for (int attempt = 0; attempt < 120; attempt++) {
          if (!process.isAlive()) throw new AssertionError(Files.readString(output));
          try {
            var request =
                HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + "/actuator/health/readiness"))
                    .timeout(Duration.ofSeconds(1))
                    .GET()
                    .build();
            if (client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode() == 200)
              return;
          } catch (java.io.IOException ignored) {
          }
          Thread.sleep(250);
        }
        throw new AssertionError("Backend did not become ready: " + Files.readString(output));
      } catch (Exception | AssertionError error) {
        close();
        throw error;
      }
    }

    @Override
    public void close() throws Exception {
      // Only this test's own child JVM, never an application process on 8080/18081.
      process.destroy();
      if (!process.waitFor(10, TimeUnit.SECONDS)) {
        process.destroyForcibly();
        assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Owned test process did not stop");
      }
    }
  }

  HttpResponse<String> request(
      HttpClient client, Backend backend, String token, String method, String path, String body)
      throws Exception {
    var builder =
        HttpRequest.newBuilder(
                URI.create(
                    "http://127.0.0.1:" + backend.port + "/api/v1/organizations/" + org + path))
            .timeout(Duration.ofSeconds(15))
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json");
    return client.send(
        builder
            .method(
                method,
                body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @Test
  void receiptsSurviveTwoSeparateBackendProcesses() throws Exception {
    GenericContainer<?> container = null;
    String endpoint = System.getenv("TEST_DYNAMODB_ENDPOINT");
    if (endpoint == null) {
      container =
          new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:2.6.1"))
              .withExposedPorts(8000)
              .withCommand("-jar DynamoDBLocal.jar -inMemory -sharedDb");
      container.start();
      endpoint = "http://" + container.getHost() + ":" + container.getMappedPort(8000);
    } else {
      assertTrue(
          Set.of("localhost", "127.0.0.1", "::1").contains(URI.create(endpoint).getHost()),
          "External test database must be loopback");
    }
    RSAKey key = new RSAKeyGenerator(2048).keyID("restart").generate();
    var identity = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    identity.createContext(
        "/jwks",
        exchange -> {
          byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    identity.start();
    try (var db =
            DynamoDbClient.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")))
                .build();
        var client = HttpClient.newHttpClient()) {
      db.createTable(
          b ->
              b.tableName(table)
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
      String admin = token(key, "ADMIN"), machine = token(key, "INGESTOR");
      String a =
          "{\"reportId\":\"a\",\"sequence\":1,\"reportedAt\":\"2026-01-01T00:00:00Z\",\"encryption\":false}";
      String b =
          "{\"reportId\":\"b\",\"sequence\":2,\"reportedAt\":\"2026-01-02T00:00:00Z\",\"encryption\":true}";
      String path;
      JsonNode current;
      JsonNode audit;
      JsonNode evidence;
      long firstPid;
      try (var first = new Backend(endpoint, identity.getAddress().getPort(), "first")) {
        firstPid = first.process.pid();
        var registered =
            request(
                client,
                first,
                admin,
                "POST",
                "/devices",
                "{\"name\":\"Restart Mac\",\"type\":\"MAC\",\"model\":\"Mac\",\"os\":\"macOS\",\"serial\":\"RESTART-001\"}");
        assertEquals(201, registered.statusCode());
        path = "/devices/" + json.readTree(registered.body()).get("id").asText();
        assertEquals(
            200, request(client, first, machine, "POST", path + "/reports", a).statusCode());
        var latest = request(client, first, machine, "POST", path + "/reports", b);
        assertEquals(200, latest.statusCode());
        current = json.readTree(latest.body());
        var replay = request(client, first, machine, "POST", path + "/reports", a);
        assertEquals(200, replay.statusCode());
        assertEquals(current, json.readTree(replay.body()));
        audit = json.readTree(request(client, first, admin, "GET", "/audit-events", null).body());
        evidence =
            json.readTree(request(client, first, admin, "GET", path + "/evaluations", null).body());
      }
      try (var restarted = new Backend(endpoint, identity.getAddress().getPort(), "restarted")) {
        assertNotEquals(firstPid, restarted.process.pid());
        var replay = request(client, restarted, machine, "POST", path + "/reports", a);
        assertEquals(200, replay.statusCode());
        assertEquals(current, json.readTree(replay.body()));
        assertEquals(
            409,
            request(
                    client,
                    restarted,
                    machine,
                    "POST",
                    path + "/reports",
                    a.replace("false", "true"))
                .statusCode());
        assertEquals(
            409,
            request(
                    client,
                    restarted,
                    machine,
                    "POST",
                    path + "/reports",
                    a.replace("\"sequence\":1", "\"sequence\":3")
                        .replace("2026-01-01", "2026-01-03"))
                .statusCode());
        assertEquals(
            409,
            request(
                    client,
                    restarted,
                    machine,
                    "POST",
                    path + "/reports",
                    a.replace("\"a\"", "\"unseen\""))
                .statusCode());
        assertEquals(
            current, json.readTree(request(client, restarted, admin, "GET", path, null).body()));
        assertEquals(
            audit,
            json.readTree(request(client, restarted, admin, "GET", "/audit-events", null).body()));
        assertEquals(
            evidence,
            json.readTree(
                request(client, restarted, admin, "GET", path + "/evaluations", null).body()));
      }
    } finally {
      identity.stop(0);
      if (container != null) container.stop();
    }
  }
}
