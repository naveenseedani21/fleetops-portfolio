package dev.fleetops.devices;
import jakarta.validation.constraints.*;
import java.time.Instant;
public final class DeviceRequests {
 private DeviceRequests(){}
 public record Register(@NotBlank @Size(max=100) String name,@NotBlank @Pattern(regexp="MAC|IPHONE|IPAD") String type,@NotBlank @Size(max=100) String model,@NotBlank @Pattern(regexp="macOS|iOS|iPadOS") String os,@Pattern(regexp="[0-9]{1,5}(\\.[0-9]{1,5}){0,3}") String osVersion,@NotBlank @Pattern(regexp="[A-Za-z0-9-]{3,64}") String serial,@Size(max=100) String employee) {}
 public record Metadata(@Min(1) long revision,@NotBlank @Size(max=100) String name,@Size(max=100) String employee) {}
 public record Revision(@Min(1) long revision) {}
 public record Report(@NotBlank @Pattern(regexp="[A-Za-z0-9-]{1,64}") String reportId,@Min(1) long sequence,@NotNull Instant reportedAt,@Pattern(regexp="[0-9]{1,5}(\\.[0-9]{1,5}){0,3}") String osVersion,Boolean encryption,Boolean screenLock,Boolean firewall) {}
}
