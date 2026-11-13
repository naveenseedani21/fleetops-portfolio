package dev.fleetops.audit;
import dev.fleetops.identity.TenantAccess;
import dev.fleetops.persistence.Store;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1")
public class ReadController {
 private final TenantAccess access;private final Store store;
 public ReadController(TenantAccess access,Store store){this.access=access;this.store=store;}
 @GetMapping("/organizations") public Map<String,String> organizations(){return access.memberships();}
 @GetMapping("/organizations/{org}/audit-events") public Store.Page<Audit> audit(@PathVariable String org,@RequestParam(defaultValue="25") int limit,@RequestParam(required=false) String cursor){access.require(org,false);return store.list(org,"AUDIT#",limit,cursor,Audit.class,true);}
}
