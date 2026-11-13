package dev.fleetops;

import static org.junit.jupiter.api.Assertions.*;

import dev.fleetops.devices.Device;
import dev.fleetops.reporting.DashboardService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class DashboardTest {
  private final Instant now = Instant.parse("2026-01-01T00:00:00Z");

  private Device d(String status, String compliance, Instant checkin) {
    return new Device(
        "id",
        "demo",
        "Device",
        "MAC",
        "Apple",
        "Mac",
        "macOS",
        "15",
        "SYN-001",
        null,
        status,
        true,
        true,
        true,
        now,
        checkin,
        compliance,
        1,
        1,
        null,
        null,
        now,
        now);
  }

  @Test
  void countsDoNotTreatUnknownOrUnmanagedAsPassing() {
    var result =
        DashboardService.calculate(
            List.of(
                d("ACTIVE", "COMPLIANT", now),
                d("NONCOMPLIANT", "NONCOMPLIANT", now),
                d("REGISTERED", "UNKNOWN", null),
                d("ACTIVE", "NOT_APPLICABLE", now.minusSeconds(86401)),
                d("RETIRED", "COMPLIANT", now)),
            now);
    assertEquals(5, result.total());
    assertEquals(1, result.retired());
    assertEquals(2, result.active());
    assertEquals(2, result.stale());
    assertEquals(1, result.noncompliant());
    assertEquals(1, result.unknown());
    assertEquals(2, result.evaluated());
    assertEquals(50.0, result.compliancePercent());
    assertEquals(4L, result.operatingSystems().get("macOS"));
  }

  @Test
  void emptyFleetHasNoMisleadingPercentage() {
    assertNull(DashboardService.calculate(List.of(), now).compliancePercent());
  }
}
