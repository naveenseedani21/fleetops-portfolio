package dev.fleetops;

import static org.junit.jupiter.api.Assertions.*;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

/** Real Python CLI -> HTTP -> signed JWT validation -> DynamoDB Local workflow. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimulatorWorkflowTest {
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
  static final String ORG = "sim-test-" + UUID.randomUUID();
  static final String TABLE = "fleetops-sim-test-" + UUID.randomUUID();
  static final String ISSUER = "https://simulator-issuer.example.test";
  static final RSAKey KEY;
  static final HttpServer IDENTITY;

  static {
    try {
      KEY = new RSAKeyGenerator(2048).keyID("simulator-test").generate();
      IDENTITY = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      IDENTITY.createContext(
          "/jwks",
          exchange -> {
            byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      IDENTITY.createContext(
          "/protocol/openid-connect/token",
          exchange -> {
            String request =
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean permitted =
                "POST".equals(exchange.getRequestMethod())
                    && request.contains("client_id=fleetops-simulator")
                    && request.contains("grant_type=client_credentials")
                    && request.contains("client_secret=workflow-fixture");
            try {
              byte[] body =
                  (permitted
                          ? "{\"access_token\":\"" + token("INGESTOR") + "\",\"expires_in\":300}"
                          : "{}")
                      .getBytes(StandardCharsets.UTF_8);
              exchange.getResponseHeaders().add("Content-Type", "application/json");
              exchange.sendResponseHeaders(permitted ? 200 : 401, body.length);
              exchange.getResponseBody().write(body);
            } catch (Exception error) {
              throw new java.io.IOException("Unable to issue test token", error);
            } finally {
              exchange.close();
            }
          });
      IDENTITY.start();
    } catch (Exception error) {
      throw new ExceptionInInitializerError(error);
    }
  }

  static String token(String role) throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .subject("workflow-" + role)
            .issuer(ISSUER)
            .audience("fleetops-api")
            .issueTime(Date.from(Instant.now().minusSeconds(5)))
            .expirationTime(Date.from(Instant.now().plusSeconds(300)))
            .claim("organizations", Map.of(ORG, role))
            .build();
    var signed =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims);
    signed.sign(new RSASSASigner(KEY));
    return signed.serialize();
  }

  @DynamicPropertySource
  static void config(DynamicPropertyRegistry registry) {
    registry.add("fleetops.dynamodb.endpoint", () -> ENDPOINT);
    registry.add("fleetops.dynamodb.table", () -> TABLE);
    registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> "http://127.0.0.1:" + IDENTITY.getAddress().getPort() + "/jwks");
  }

  @LocalServerPort int port;
  @Autowired DynamoDbClient db;
  @TempDir Path temporary;

  @AfterAll
  static void stop() {
    IDENTITY.stop(0);
    if (container != null) container.stop();
  }

  @Test
  void administratorProvisionsAndMachineReportsWithoutCorruptingReplayState() throws Exception {
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
                    KeySchemaElement.builder().attributeName("sk").keyType(KeyType.RANGE).build()));
    Path script =
        Path.of("..", "simulator", "scenarios", "verify_reports.py").toAbsolutePath().normalize();
    assertTrue(
        Files.isRegularFile(script),
        "Run from the backend Maven module with its sibling simulator source");
    Path output = temporary.resolve("workflow.log");
    var builder =
        new ProcessBuilder(
            "python3",
            script.toString(),
            "http://127.0.0.1:" + port,
            "http://127.0.0.1:" + IDENTITY.getAddress().getPort(),
            ORG,
            temporary.resolve("state.json").toString());
    builder.environment().put("FLEETOPS_ADMIN_TOKEN", token("ADMIN"));
    builder.environment().put("FLEETOPS_CLIENT_SECRET", "workflow-fixture");
    builder.redirectErrorStream(true).redirectOutput(output.toFile());
    var process = builder.start();
    boolean finished = process.waitFor(90, TimeUnit.SECONDS);
    if (!finished) process.destroyForcibly();
    assertTrue(finished, "Simulator workflow exceeded its bounded runtime");
    assertEquals(0, process.exitValue(), Files.readString(output));
    assertTrue(Files.readString(output).contains("unchanged persisted evidence"));
  }
}
