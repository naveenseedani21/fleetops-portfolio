package dev.fleetops.identity;

import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class TenantAccess {
  public Jwt identity() {
    var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt))
      throw new AccessDeniedException("Authentication required");
    return jwt;
  }

  public Map<String, String> memberships() {
    Object claim = identity().getClaims().get("organizations");
    if (!(claim instanceof Map<?, ?> raw)) return Map.of();
    var result = new java.util.TreeMap<String, String>();
    raw.forEach(
        (key, value) -> {
          if (key instanceof String k
              && value instanceof String v
              && java.util.Set.of("ADMIN", "VIEWER", "INGESTOR").contains(v)) result.put(k, v);
        });
    return Map.copyOf(result);
  }

  public String require(String tenant, boolean write) {
    String role = memberships().get(tenant);
    if (role == null || (write && !role.equals("ADMIN")) || role.equals("INGESTOR"))
      throw new AccessDeniedException("Tenant permission denied");
    return identity().getSubject();
  }

  public String ingest(String tenant, String deviceId) {
    String role = memberships().get(tenant);
    if (!"ADMIN".equals(role) && !"INGESTOR".equals(role))
      throw new AccessDeniedException("Telemetry permission denied");
    if ("INGESTOR".equals(role) && identity().getClaims().containsKey("device_scopes")) {
      Object claim = identity().getClaims().get("device_scopes");
      if (!(claim instanceof Map<?, ?> scopes)
          || !(scopes.get(tenant) instanceof java.util.List<?> ids)
          || ids.stream().anyMatch(id -> !(id instanceof String))
          || !ids.contains(deviceId))
        throw new AccessDeniedException("Device scope permission denied");
    }
    return identity().getSubject();
  }
}
