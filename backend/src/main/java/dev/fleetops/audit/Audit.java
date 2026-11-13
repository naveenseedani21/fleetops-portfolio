package dev.fleetops.audit;

import dev.fleetops.persistence.Store.Change;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.MDC;

public record Audit(
    String id,
    String organizationId,
    String actorId,
    String action,
    String targetId,
    Instant timestamp,
    String requestId,
    String outcome) {
  public static Change change(String org, String actor, String action, String target, Instant now) {
    String id = UUID.randomUUID().toString();
    return new Change(
        "AUDIT#" + dev.fleetops.persistence.HistoryKeys.timestamp(now) + "#" + id,
        new Audit(id, org, actor, action, target, now, MDC.get("request.id"), "SUCCESS"),
        1,
        null);
  }
}
