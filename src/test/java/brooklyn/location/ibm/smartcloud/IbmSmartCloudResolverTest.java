package brooklyn.location.ibm.smartcloud;

import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.testng.annotations.Test;

import brooklyn.location.Location;
import brooklyn.location.basic.LocationResolverTest;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.MutableMap;

public class IbmSmartCloudResolverTest {

   public static final Map<String, String> PROPS = MutableMap.of("brooklyn.ibm-smartcloud.identity", "x",
           "brooklyn.ibm-smartcloud.credential", "y", "brooklyn.ibm-smartcloud.user", "idcuser");

   @Test
   public void testIbmSmartCloudLoads() {
      ManagementContext managementContext = new LocalManagementContext();
      Location l = managementContext.getLocationRegistry().resolve("ibm-smartcloud", PROPS);
      assertTrue(l instanceof IbmSmartCloudLocation);
   }
}
