package dev.fleetops.devices;

import java.time.Instant;

public record Device(
    String id,
    String organizationId,
    String name,
    String type,
    String manufacturer,
    String model,
    String os,
    String osVersion,
    String serial,
    String employee,
    String status,
    Boolean encryption,
    Boolean screenLock,
    Boolean firewall,
    Instant enrolledAt,
    Instant lastCheckIn,
    String compliance,
    long revision,
    long sequence,
    String lastReportId,
    String lastReportHash,
    Instant createdAt,
    Instant updatedAt) {}
