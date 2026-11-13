package dev.fleetops.policies;
import java.time.Instant;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record Policy(String id,String organizationId,String name,String description,List<String> targetTypes,List<Rule> rules,String severity,boolean enabled,long version,String createdBy,Instant createdAt,Instant updatedAt) {
 public record Rule(@NotBlank @Pattern(regexp="MIN_OS|ENCRYPTION|SCREEN_LOCK|FIREWALL|MAX_AGE_HOURS|ALLOWED_TYPE") String type,@NotBlank @Size(max=100) String value) {}
 public record Input(@Min(0) long version,@NotBlank @Size(max=100) String name,@Size(max=500) String description,@NotEmpty @Size(max=3) List<@Pattern(regexp="MAC|IPHONE|IPAD") String> targetTypes,@NotEmpty @Size(max=12) List<@Valid Rule> rules,@NotBlank @Pattern(regexp="LOW|MEDIUM|HIGH|CRITICAL") String severity,boolean enabled) {}
}
