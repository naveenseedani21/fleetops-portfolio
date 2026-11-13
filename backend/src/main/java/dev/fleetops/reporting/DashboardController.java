package dev.fleetops.reporting;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/organizations/{org}/dashboard")
public class DashboardController {
  private final DashboardService service;

  public DashboardController(DashboardService service) {
    this.service = service;
  }

  @GetMapping("/summary")
  public DashboardService.Summary summary(@PathVariable String org) {
    return service.summary(org);
  }
}
