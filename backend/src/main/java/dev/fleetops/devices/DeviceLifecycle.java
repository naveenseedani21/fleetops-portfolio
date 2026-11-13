package dev.fleetops.devices;

import java.util.Set;

/** Read compatibility only; no background migration or persisted-data rewrite. */
public final class DeviceLifecycle {
  private static final Set<String> STATES = Set.of("REGISTERED", "ACTIVE", "INACTIVE", "RETIRED");

  private DeviceLifecycle() {}

  public static String status(Device device) {
    // The old evaluator could mark a never-reported registration NONCOMPLIANT.
    // A historical check-in alone cannot establish its current lifecycle.
    if ("NONCOMPLIANT".equals(device.status())
        && device.sequence() == 0
        && device.lastCheckIn() == null
        && device.lastReportId() == null
        && device.lastReportHash() == null) return "REGISTERED";
    return device.status();
  }

  public static boolean requiresReview(Device device) {
    String status = status(device);
    return status == null || !STATES.contains(status);
  }

  public static Device readable(Device d) {
    String status = status(d);
    if (java.util.Objects.equals(status, d.status())) return d;
    return new Device(
        d.id(),
        d.organizationId(),
        d.name(),
        d.type(),
        d.manufacturer(),
        d.model(),
        d.os(),
        d.osVersion(),
        d.serial(),
        d.employee(),
        status,
        d.encryption(),
        d.screenLock(),
        d.firewall(),
        d.enrolledAt(),
        d.lastCheckIn(),
        d.compliance(),
        d.revision(),
        d.sequence(),
        d.lastReportId(),
        d.lastReportHash(),
        d.createdAt(),
        d.updatedAt());
  }
}
