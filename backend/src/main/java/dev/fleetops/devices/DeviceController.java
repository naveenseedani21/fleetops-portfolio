package dev.fleetops.devices;

import dev.fleetops.persistence.Store.Page;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}/devices")
public class DeviceController {
  private final DeviceService service;

  public DeviceController(DeviceService service) {
    this.service = service;
  }

  @GetMapping
  public Page<Device> list(
      @PathVariable String org,
      @RequestParam(defaultValue = "25") int limit,
      @RequestParam(required = false) String cursor) {
    return service.list(org, limit, cursor);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Device register(
      @PathVariable String org, @Valid @RequestBody DeviceRequests.Register input) {
    return service.register(org, input);
  }

  @GetMapping("/{id}")
  public Device get(@PathVariable String org, @PathVariable String id) {
    return service.get(org, id);
  }

  @PatchMapping("/{id}")
  public Device update(
      @PathVariable String org,
      @PathVariable String id,
      @Valid @RequestBody DeviceRequests.Metadata input) {
    return service.metadata(org, id, input);
  }

  @PostMapping("/{id}/retire")
  public Device retire(
      @PathVariable String org,
      @PathVariable String id,
      @Valid @RequestBody DeviceRequests.Revision input) {
    return service.retire(org, id, input.revision());
  }

  @PostMapping("/{id}/reports")
  public Device report(
      @PathVariable String org,
      @PathVariable String id,
      @Valid @RequestBody DeviceRequests.Report input) {
    return service.report(org, id, input);
  }

  @PostMapping("/{id}/evaluations")
  public DeviceService.Evidence evaluate(@PathVariable String org, @PathVariable String id) {
    return service.evaluate(org, id);
  }

  @GetMapping("/{id}/evaluations")
  public Page<DeviceService.Evidence> history(
      @PathVariable String org,
      @PathVariable String id,
      @RequestParam(defaultValue = "25") int limit,
      @RequestParam(required = false) String cursor) {
    return service.history(org, id, limit, cursor);
  }
}
