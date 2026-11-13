package dev.fleetops.policies;

import dev.fleetops.persistence.Store.Page;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}/policies")
public class PolicyController {
  private final PolicyService service;

  public PolicyController(PolicyService service) {
    this.service = service;
  }

  @GetMapping
  public Page<Policy> list(
      @PathVariable String org,
      @RequestParam(defaultValue = "25") int limit,
      @RequestParam(required = false) String cursor) {
    return service.list(org, limit, cursor);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Policy create(@PathVariable String org, @Valid @RequestBody Policy.Input input) {
    return service.create(org, input);
  }

  @PutMapping("/{id}")
  public Policy update(
      @PathVariable String org, @PathVariable String id, @Valid @RequestBody Policy.Input input) {
    return service.update(org, id, input);
  }

  @GetMapping("/{id}/history")
  public Page<Policy> history(
      @PathVariable String org,
      @PathVariable String id,
      @RequestParam(defaultValue = "25") int limit,
      @RequestParam(required = false) String cursor) {
    return service.history(org, id, limit, cursor);
  }
}
