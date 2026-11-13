package dev.fleetops.policies;

import dev.fleetops.audit.Audit;
import dev.fleetops.compliance.Engine;
import dev.fleetops.identity.TenantAccess;
import dev.fleetops.persistence.Store;
import dev.fleetops.shared.ApiException;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PolicyService {
  private final Store store;
  private final TenantAccess access;
  private final Engine engine;

  public PolicyService(Store store, TenantAccess access, Engine engine) {
    this.store = store;
    this.access = access;
    this.engine = engine;
  }

  public Store.Page<Policy> list(String org, int limit, String cursor) {
    access.require(org, false);
    return store.list(org, "POLICY#", limit, cursor, Policy.class, false);
  }

  public Policy create(String org, Policy.Input input) {
    access.require(org, true);
    if (input.version() != 0) throw ApiException.invalid("New policy version must be zero");
    return save(org, UUID.randomUUID().toString(), input, null);
  }

  public Policy update(String org, String id, Policy.Input input) {
    access.require(org, true);
    var previous = store.get(org, "POLICY#" + id, Policy.class);
    if (input.version() != previous.version()) throw ApiException.conflict();
    return save(org, id, input, previous);
  }

  public Store.Page<Policy> history(String org, String id, int limit, String cursor) {
    access.require(org, false);
    return store.list(org, "PV#" + id + "#", limit, cursor, Policy.class, true);
  }

  private Policy save(String org, String id, Policy.Input input, Policy previous) {
    String actor = access.require(org, true);
    engine.validate(input);
    Instant now = Instant.now();
    long version = previous == null ? 1 : previous.version() + 1;
    var policy =
        new Policy(
            id,
            org,
            input.name(),
            input.description(),
            List.copyOf(input.targetTypes()),
            List.copyOf(input.rules()),
            input.severity(),
            input.enabled(),
            version,
            previous == null ? actor : previous.createdBy(),
            previous == null ? now : previous.createdAt(),
            now);
    store.commit(
        org,
        List.of(
            new Store.Change(
                "POLICY#" + id, policy, version, previous == null ? null : previous.version()),
            new Store.Change(
                "PV#" + id + "#" + String.format("%020d", version), policy, version, null),
            Audit.change(
                org, actor, previous == null ? "POLICY_CREATED" : "POLICY_UPDATED", id, now)));
    return policy;
  }
}
