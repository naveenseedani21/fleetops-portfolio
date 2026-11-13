package dev.fleetops;
import static org.junit.jupiter.api.Assertions.*;
import dev.fleetops.compliance.Engine;
import dev.fleetops.devices.Device;
import dev.fleetops.policies.Policy;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
class EngineTest {
 private final Engine engine=new Engine();
 private final Instant now=Instant.parse("2026-01-01T00:00:00Z");
 private Device device(String type,String os,Boolean encryption,Boolean lock,Boolean firewall,Instant checkin){return new Device("d","a","Test",type,"Apple","Synthetic","macOS",os,"SYN-001",null,"ACTIVE",encryption,lock,firewall,now,checkin,"UNKNOWN",1,1,null,null,now,now);}
 private String eval(Device d,String rule,String value){var p=new Policy("p","a","Baseline","",List.of("MAC","IPHONE","IPAD"),List.of(new Policy.Rule(rule,value)),"HIGH",true,1,"u",now,now);return engine.evaluate(d,p,now).status();}
 @Test void numericVersions(){assertTrue(Engine.compare("15.10","15.9")>0);assertEquals(0,Engine.compare("15.0","15"));assertTrue(Engine.compare("14.9.9","15")<0);}
 @Test void missingIsUnknown(){assertEquals("UNKNOWN",eval(device("MAC",null,null,null,null,null),"ENCRYPTION","true"));assertEquals("UNKNOWN",eval(device("MAC",null,null,null,null,null),"MIN_OS","15"));assertEquals("UNKNOWN",eval(device("MAC",null,null,null,null,null),"MAX_AGE_HOURS","24"));}
 @Test void securityRules(){var d=device("MAC","15.10",true,false,false,now);assertEquals("COMPLIANT",eval(d,"ENCRYPTION","true"));assertEquals("NONCOMPLIANT",eval(d,"SCREEN_LOCK","true"));assertEquals("NONCOMPLIANT",eval(d,"FIREWALL","true"));assertEquals("COMPLIANT",eval(d,"MIN_OS","15.9"));assertEquals("NONCOMPLIANT",eval(d,"ALLOWED_TYPE","IPAD"));}
 @Test void firewallNotApplicableToPhone(){assertEquals("NOT_APPLICABLE",eval(device("IPHONE","18",null,null,null,now),"FIREWALL","true"));}
 @Test void checkinBoundary(){assertEquals("COMPLIANT",eval(device("MAC","15",true,true,true,now.minusSeconds(86400)),"MAX_AGE_HOURS","24"));assertEquals("NONCOMPLIANT",eval(device("MAC","15",true,true,true,now.minusSeconds(86401)),"MAX_AGE_HOURS","24"));}
 @Test void precedence(){assertEquals("NONCOMPLIANT",Engine.aggregate(List.of("UNKNOWN","NONCOMPLIANT")));assertEquals("UNKNOWN",Engine.aggregate(List.of("COMPLIANT","UNKNOWN")));assertEquals("NOT_APPLICABLE",Engine.aggregate(List.of()));}
 @Test void invalidRulesRejected(){var input=new Policy.Input(0,"Baseline","",List.of("MAC"),List.of(new Policy.Rule("MIN_OS","banana")),"HIGH",true);assertThrows(dev.fleetops.shared.ApiException.class,()->engine.validate(input));}
}
