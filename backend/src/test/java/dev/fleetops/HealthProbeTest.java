package dev.fleetops;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.*;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.DescribeTableResponse;

@SpringBootTest
@AutoConfigureMockMvc
class HealthProbeTest {
  @Autowired MockMvc mvc;
  @Autowired ApplicationContext context;
  @MockitoBean DynamoDbClient db;

  @BeforeEach
  void ready() {
    AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
    AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
  }

  @Test
  void databaseOutageDoesNotFailLivenessOrExposeDetails() throws Exception {
    doThrow(new IllegalStateException("private diagnostic"))
        .when(db)
        .describeTable(any(Consumer.class));
    mvc.perform(get("/actuator/health/liveness"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
    verifyNoInteractions(db);
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(content().json("{\"status\":\"DOWN\"}", true));
    verify(db).describeTable(any(Consumer.class));
  }

  @Test
  void recoveredDependencyRestoresReadiness() throws Exception {
    doThrow(new IllegalStateException("database unavailable"))
        .doReturn(DescribeTableResponse.builder().build())
        .when(db)
        .describeTable(any(Consumer.class));
    mvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable());
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isOk())
        .andExpect(content().json("{\"status\":\"UP\"}", true));
  }

  @Test
  void applicationRefusingTrafficIsNotReadyEvenWithHealthyDatabase() throws Exception {
    AvailabilityChangeEvent.publish(context, ReadinessState.REFUSING_TRAFFIC);
    mvc.perform(get("/actuator/health/readiness"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));
    mvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
  }
}
