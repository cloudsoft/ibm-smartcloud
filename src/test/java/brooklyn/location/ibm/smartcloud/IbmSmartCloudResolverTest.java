package brooklyn.location.ibm.smartcloud;

import brooklyn.location.basic.LocationResolverTest;
import brooklyn.util.MutableMap;
import org.testng.annotations.Test;

import java.util.Map;

import static org.testng.Assert.assertTrue;

public class IbmSmartCloudResolverTest {

   public static final Map<String, String> PROPS = MutableMap.of("brooklyn.enstratius.identity", "x",
           "brooklyn.enstratius.credential", "y");

   @Test
   public void testIbmSmartCloudLoads() {
      assertTrue(LocationResolverTest.resolve(PROPS, "ibm-smartcloud:us-east-1:eu-west-1a") instanceof
              IbmSmartCloudLocation);
   }
}
