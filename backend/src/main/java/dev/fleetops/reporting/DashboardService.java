package dev.fleetops.reporting;

import dev.fleetops.devices.Device;
import dev.fleetops.devices.DeviceLifecycle;
import dev.fleetops.identity.TenantAccess;
import dev.fleetops.persistence.Store;
import dev.fleetops.shared.ApiException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
  public record Summary(
      int total,
      int retired,
      int active,
      int stale,
      int noncompliant,
      int unknown,
      int evaluated,
      Double compliancePercent,
      Map<String, Long> operatingSystems,
      Map<String, Long> compliance,
      int lifecycleReviewRequired,
      Instant calculatedAt) {}

  private final Store store;
  private final TenantAccess access;

  public DashboardService(Store store, TenantAccess access) {
    this.store = store;
    this.access = access;
  }

  public Summary summary(String org) {
    access.require(org, false);
    var devices = new ArrayList<Device>();
    String cursor = null;
    do {
      var page = store.list(org, "DEVICE#", 100, cursor, Device.class, false);
      devices.addAll(page.items());
      if (devices.size() > 1000)
        throw ApiException.invalid(
            "Live summary supports up to 1000 devices; materialized reporting is required for larger fleets");
      cursor = page.nextCursor();
    } while (cursor != null);
    return calculate(devices, Instant.now());
  }

  public static Summary calculate(List<Device> devices, Instant now) {
    int retired = 0, active = 0, stale = 0, failed = 0, unknown = 0, passed = 0, review = 0;
    var os = new TreeMap<String, Long>();
    var compliance = new TreeMap<String, Long>();
    for (var d : devices) {
      if ("RETIRED".equals(DeviceLifecycle.status(d))) {
        retired++;
        continue;
      }
      os.merge(d.os(), 1L, Long::sum);
      compliance.merge(d.compliance(), 1L, Long::sum);
      if (d.lastCheckIn() == null || d.lastCheckIn().isBefore(now.minusSeconds(86400))) stale++;
      if ("ACTIVE".equals(DeviceLifecycle.status(d))) active++;
      if (DeviceLifecycle.requiresReview(d)) review++;
      switch (d.compliance()) {
        case "COMPLIANT" -> passed++;
        case "NONCOMPLIANT" -> failed++;
        case "UNKNOWN" -> unknown++;
        default -> {}
      }
    }
    int evaluated = passed + failed;
    return new Summary(
        devices.size(),
        retired,
        active,
        stale,
        failed,
        unknown,
        evaluated,
        evaluated == 0 ? null : Math.round(1000.0 * passed / evaluated) / 10.0,
        Map.copyOf(os),
        Map.copyOf(compliance),
        review,
        now);
  }
}
