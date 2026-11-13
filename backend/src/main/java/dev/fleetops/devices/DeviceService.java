package dev.fleetops.devices;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.fleetops.audit.Audit;
import dev.fleetops.compliance.Engine;
import dev.fleetops.identity.TenantAccess;
import dev.fleetops.persistence.Store;
import dev.fleetops.policies.Policy;
import dev.fleetops.shared.ApiException;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class DeviceService {
 public record Evidence(String id,String organizationId,String deviceId,Instant evaluatedAt,String status,List<Engine.Evaluation> policies) {}
 private final Store store; private final TenantAccess access; private final Engine engine; private final ObjectMapper json; private final MeterRegistry metrics;
 public DeviceService(Store store,TenantAccess access,Engine engine,ObjectMapper json,MeterRegistry metrics){this.store=store;this.access=access;this.engine=engine;this.json=json;this.metrics=metrics;}
 public Store.Page<Device> list(String org,int limit,String cursor){access.require(org,false);return store.list(org,"DEVICE#",limit,cursor,Device.class,false);}
 public Device get(String org,String id){access.require(org,false);return read(org,id);}
 private Device read(String org,String id){return store.get(org,"DEVICE#"+id,Device.class);}
 public Device register(String org,DeviceRequests.Register input){
  String actor=access.ingest(org);Instant now=Instant.now();String id=UUID.randomUUID().toString();
  String expectedOs=switch(input.type()){case "MAC" -> "macOS";case "IPHONE" -> "iOS";default -> "iPadOS";};
  if(!expectedOs.equals(input.os()))throw ApiException.invalid("Operating system does not match device type");
  String serial=input.serial().toUpperCase(Locale.ROOT);
  var d=new Device(id,org,input.name(),input.type(),"Apple",input.model(),input.os(),input.osVersion(),serial,input.employee(),"REGISTERED",null,null,null,now,null,"UNKNOWN",1,0,null,null,now,now);
  store.commit(org,List.of(new Store.Change("DEVICE#"+id,d,1,null),new Store.Change("SERIAL#"+serial,Map.of("deviceId",id),1,null),Audit.change(org,actor,"DEVICE_REGISTERED",id,now)));
  return d;
 }
 public Device metadata(String org,String id,DeviceRequests.Metadata input){
  String actor=access.require(org,true);Device d=read(org,id);assertRevision(d,input.revision());assertLive(d);
  Device updated=copy(d,input.name(),input.employee(),d.status(),d.osVersion(),d.encryption(),d.screenLock(),d.firewall(),d.lastCheckIn(),d.compliance(),d.sequence(),d.lastReportId(),d.lastReportHash());
  persist(org,d,updated,actor,"DEVICE_UPDATED",List.of());return updated;
 }
 public Device retire(String org,String id,long revision){
  String actor=access.require(org,true);Device d=read(org,id);assertRevision(d,revision);assertLive(d);
  Device updated=copy(d,d.name(),d.employee(),"RETIRED",d.osVersion(),d.encryption(),d.screenLock(),d.firewall(),d.lastCheckIn(),d.compliance(),d.sequence(),d.lastReportId(),d.lastReportHash());
  persist(org,d,updated,actor,"DEVICE_RETIRED",List.of());return updated;
 }
 public Device report(String org,String id,DeviceRequests.Report input){
  String actor=access.ingest(org);Device d=read(org,id);assertLive(d);String hash=digest(input);
  if(input.sequence()==d.sequence() && input.reportId().equals(d.lastReportId()) && hash.equals(d.lastReportHash()))return d;
  if(input.sequence()<=d.sequence() || input.reportId().equals(d.lastReportId()))throw ApiException.conflict();
  Instant now=Instant.now();
  if(input.reportedAt().isAfter(now.plusSeconds(300)) || (d.lastCheckIn()!=null && input.reportedAt().isBefore(d.lastCheckIn())))throw ApiException.invalid("Report time is future-dated or older than current state");
  var proposed=copy(d,d.name(),d.employee(),"ACTIVE",input.osVersion(),input.encryption(),input.screenLock(),input.firewall(),input.reportedAt(),"UNKNOWN",input.sequence(),input.reportId(),hash);
  var evidence=evidence(org,proposed,now);
  var updated=withCompliance(proposed,evidence.status());
  persist(org,d,updated,actor,"DEVICE_REPORT_ACCEPTED",List.of(evidenceChange(evidence),Audit.change(org,actor,"COMPLIANCE_EVALUATED",id,now)));
  metrics.counter("fleetops.reports.accepted").increment();return updated;
 }
 public Evidence evaluate(String org,String id){
  String actor=access.require(org,true);Device d=read(org,id);assertLive(d);
  var evidence=evidence(org,d,Instant.now());
  Device updated=copy(d,d.name(),d.employee(),d.status(),d.osVersion(),d.encryption(),d.screenLock(),d.firewall(),d.lastCheckIn(),evidence.status(),d.sequence(),d.lastReportId(),d.lastReportHash());
  updated=withCompliance(updated,evidence.status());
  persist(org,d,updated,actor,"COMPLIANCE_EVALUATED",List.of(evidenceChange(evidence)));return evidence;
 }
 private Evidence evidence(String org,Device d,Instant now){
  long start=System.nanoTime();
  var policies=store.list(org,"POLICY#",21,null,Policy.class,false);
  if(policies.items().size()>20 || policies.nextCursor()!=null)throw ApiException.invalid("Initial evaluator supports at most 20 policies per organization");
  var results=policies.items().stream().map(p -> engine.evaluate(d,p,now)).toList();
  String status=Engine.aggregate(results.stream().map(Engine.Evaluation::status).toList());
  metrics.timer("fleetops.compliance.duration").record(System.nanoTime()-start,java.util.concurrent.TimeUnit.NANOSECONDS);
  return new Evidence(UUID.randomUUID().toString(),org,d.id(),now,status,results);
 }
 private Store.Change evidenceChange(Evidence e){return new Store.Change("EVAL#"+e.deviceId()+"#"+e.evaluatedAt()+"#"+e.id(),e,1,null);}
 private void persist(String org,Device previous,Device next,String actor,String action,List<Store.Change> extra){
  var changes=new ArrayList<>(extra);changes.add(new Store.Change("DEVICE#"+next.id(),next,next.revision(),previous.revision()));changes.add(Audit.change(org,actor,action,next.id(),Instant.now()));store.commit(org,changes);
 }
 public Store.Page<Evidence> history(String org,String id,int limit,String cursor){access.require(org,false);read(org,id);return store.list(org,"EVAL#"+id+"#",limit,cursor,Evidence.class,true);}
 private static void assertRevision(Device d,long revision){if(d.revision()!=revision)throw ApiException.conflict();}
 private static void assertLive(Device d){if(d.status().equals("RETIRED"))throw ApiException.invalid("Retired devices cannot be modified or evaluated");}
 private String digest(Object value){try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(value)));}catch(Exception e){throw new IllegalStateException(e);}}
 private Device withCompliance(Device d,String compliance){
  String status=compliance.equals("NONCOMPLIANT")?"NONCOMPLIANT":d.lastCheckIn()==null?"REGISTERED":"ACTIVE";
  return new Device(d.id(),d.organizationId(),d.name(),d.type(),d.manufacturer(),d.model(),d.os(),d.osVersion(),d.serial(),d.employee(),status,d.encryption(),d.screenLock(),d.firewall(),d.enrolledAt(),d.lastCheckIn(),compliance,d.revision(),d.sequence(),d.lastReportId(),d.lastReportHash(),d.createdAt(),d.updatedAt());
 }
 private Device copy(Device d,String name,String employee,String status,String version,Boolean encryption,Boolean lock,Boolean firewall,Instant checkin,String compliance,long sequence,String reportId,String hash){
  return new Device(d.id(),d.organizationId(),name,d.type(),d.manufacturer(),d.model(),d.os(),version,d.serial(),employee,status,encryption,lock,firewall,d.enrolledAt(),checkin,compliance,d.revision()+1,sequence,reportId,hash,d.createdAt(),Instant.now());
 }
}
