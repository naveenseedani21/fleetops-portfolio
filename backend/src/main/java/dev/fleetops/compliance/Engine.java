package dev.fleetops.compliance;
import dev.fleetops.devices.Device;
import dev.fleetops.policies.Policy;
import dev.fleetops.shared.ApiException;
import java.math.BigInteger;
import java.time.*;
import java.util.*;
import java.util.function.BiFunction;
import org.springframework.stereotype.Component;
@Component
public class Engine {
 public record RuleResult(String rule,String status,String reason) {}
 public record Evaluation(String policyId,long policyVersion,String severity,String status,Instant evaluatedAt,List<RuleResult> rules) {}
 private record Context(Device device,Instant now) {}
 private final Map<String,BiFunction<Context,String,String>> rules=Map.of(
  "MIN_OS",(c,v) -> c.device.osVersion()==null?"UNKNOWN":compare(c.device.osVersion(),v)>=0?"COMPLIANT":"NONCOMPLIANT",
  "ENCRYPTION",(c,v) -> bool(c.device.encryption(),v),
  "SCREEN_LOCK",(c,v) -> bool(c.device.screenLock(),v),
  "FIREWALL",(c,v) -> !c.device.type().equals("MAC")?"NOT_APPLICABLE":bool(c.device.firewall(),v),
  "MAX_AGE_HOURS",(c,v) -> c.device.lastCheckIn()==null?"UNKNOWN":Duration.between(c.device.lastCheckIn(),c.now).compareTo(Duration.ofHours(Long.parseLong(v)))<=0?"COMPLIANT":"NONCOMPLIANT",
  "ALLOWED_TYPE",(c,v) -> Arrays.asList(v.split(",")).contains(c.device.type())?"COMPLIANT":"NONCOMPLIANT");
 private static String bool(Boolean actual,String required){return actual==null?"UNKNOWN":actual==Boolean.parseBoolean(required)?"COMPLIANT":"NONCOMPLIANT";}
 public static int compare(String a,String b) {
  String[] x=a.split("\\."),y=b.split("\\.");
  for(int i=0;i<Math.max(x.length,y.length);i++){int c=new BigInteger(i<x.length?x[i]:"0").compareTo(new BigInteger(i<y.length?y[i]:"0"));if(c!=0)return c;}
  return 0;
 }
 public void validate(Policy.Input input) {
  var seen=new HashSet<String>();
  for(var rule:input.rules()) {
   if(!seen.add(rule.type())) throw ApiException.invalid("Duplicate rule type");
   boolean valid=switch(rule.type()) {
    case "MIN_OS" -> rule.value().matches("[0-9]{1,5}(\\.[0-9]{1,5}){0,3}");
    case "MAX_AGE_HOURS" -> rule.value().matches("[1-9][0-9]{0,4}");
    case "ALLOWED_TYPE" -> rule.value().matches("(MAC|IPHONE|IPAD)(,(MAC|IPHONE|IPAD)){0,2}");
    case "ENCRYPTION","SCREEN_LOCK","FIREWALL" -> Set.of("true","false").contains(rule.value());
    default -> false;
   };
   if(!valid) throw ApiException.invalid("Invalid rule value for "+rule.type());
  }
 }
 public Evaluation evaluate(Device device,Policy policy,Instant now) {
  if(!policy.enabled() || !policy.targetTypes().contains(device.type())) return new Evaluation(policy.id(),policy.version(),policy.severity(),"NOT_APPLICABLE",now,List.of());
  var results=policy.rules().stream().map(rule -> {
   var strategy=rules.get(rule.type());String status=strategy==null?"UNKNOWN":strategy.apply(new Context(device,now),rule.value());
   return new RuleResult(rule.type(),status,switch(status){case "NONCOMPLIANT" -> "Requirement not met";case "UNKNOWN" -> "Required telemetry unavailable";case "NOT_APPLICABLE" -> "Unsupported on this device type";default -> "Requirement met";});
  }).toList();
  return new Evaluation(policy.id(),policy.version(),policy.severity(),aggregate(results.stream().map(RuleResult::status).toList()),now,results);
 }
 public static String aggregate(List<String> statuses) {
  if(statuses.contains("NONCOMPLIANT"))return "NONCOMPLIANT";
  if(statuses.contains("UNKNOWN"))return "UNKNOWN";
  if(statuses.contains("COMPLIANT"))return "COMPLIANT";
  return "NOT_APPLICABLE";
 }
}
