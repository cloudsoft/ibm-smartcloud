package brooklyn.location.ibm.smartcloud;

import brooklyn.location.basic.LocationResolverTest;
import brooklyn.util.MutableMap;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertTrue;

public class IbmSmartCloudResolverTest {

   public static final Map<String, String> PROPS = MutableMap.of("brooklyn.ibm-smartcloud.identity", "x",
           "brooklyn.ibm-smartcloud.credential", "y", "brooklyn.ibm-smartcloud.user", "idcuser");

   @Test
   public void testIbmSmartCloudLoads() {
      assertTrue(LocationResolverTest.resolve(PROPS, "ibm-smartcloud") instanceof
              IbmSmartCloudLocation);
   }
}
