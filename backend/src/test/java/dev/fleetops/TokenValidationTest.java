package dev.fleetops;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class TokenValidationTest {
  static final RSAKey KEY;
  static final HttpServer SERVER;

  static {
    try {
      KEY = new RSAKeyGenerator(2048).keyID("test").generate();
      SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      SERVER.createContext(
          "/jwks",
          exchange -> {
            byte[] body =
                new JWKSet(KEY.toPublicJWK())
                    .toString()
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
          });
      SERVER.start();
    } catch (Exception e) {
      throw new ExceptionInInitializerError(e);
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    r.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        () -> "https://issuer.example.test");
    r.add(
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
        () -> "http://127.0.0.1:" + SERVER.getAddress().getPort() + "/jwks");
  }

  @Autowired MockMvc mvc;

  @AfterAll
  static void stop() {
    SERVER.stop(0);
  }

  String token(String issuer, String audience, Instant expiry, RSAKey key) throws Exception {
    return token(issuer, audience, expiry, key, Instant.now().minusSeconds(60));
  }

  String token(String issuer, String audience, Instant expiry, RSAKey key, Instant notBefore)
      throws Exception {
    var claims =
        new JWTClaimsSet.Builder()
            .subject("test-user")
            .issuer(issuer)
            .audience(audience)
            .issueTime(Date.from(Instant.now().minusSeconds(600)))
            .notBeforeTime(Date.from(notBefore))
            .expirationTime(Date.from(expiry))
            .claim("organizations", Map.of("demo", "VIEWER"))
            .build();
    var signed =
        new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("test").build(), claims);
    signed.sign(new RSASSASigner(key));
    return signed.serialize();
  }

  @Test
  void validatesRealSignedTokens() throws Exception {
    mvc.perform(
            get("/api/v1/organizations")
                .header(
                    "Authorization",
                    "Bearer "
                        + token(
                            "https://issuer.example.test",
                            "fleetops-api",
                            Instant.now().plusSeconds(300),
                            KEY)))
        .andExpect(status().isOk());
  }

  @Test
  void rejectsWrongIssuerAudienceExpiryAndSignature() throws Exception {
    for (String value :
        List.of(
            token(
                "https://attacker.example.test",
                "fleetops-api",
                Instant.now().plusSeconds(300),
                KEY),
            token(
                "https://issuer.example.test", "another-api", Instant.now().plusSeconds(300), KEY),
            token(
                "https://issuer.example.test",
                "fleetops-api",
                Instant.now().minusSeconds(300),
                KEY),
            token(
                "https://issuer.example.test",
                "fleetops-api",
                Instant.now().plusSeconds(300),
                new RSAKeyGenerator(2048).generate()))) {
      mvc.perform(get("/api/v1/organizations").header("Authorization", "Bearer " + value))
          .andExpect(status().isUnauthorized());
    }
  }

  @Test
  void rejectsTokenThatIsNotYetValid() throws Exception {
    mvc.perform(
            get("/api/v1/organizations")
                .header(
                    "Authorization",
                    "Bearer "
                        + token(
                            "https://issuer.example.test",
                            "fleetops-api",
                            Instant.now().plusSeconds(1200),
                            KEY,
                            Instant.now().plusSeconds(600))))
        .andExpect(status().isUnauthorized());
  }
}
