package brooklyn.location.ibm.smartcloud;

import static org.testng.Assert.assertTrue;

import java.util.Map;

import org.testng.Assert;
import org.testng.annotations.Test;

import brooklyn.config.BrooklynProperties;
import brooklyn.config.ConfigUtils;
import brooklyn.location.Location;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.ManagementContext;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.collections.MutableMap;

public class IbmSmartCloudResolverTest {

   public static final Map<String, String> FULL_PROPS = MutableMap.of(
           "brooklyn.location.ibm-smartcloud.identity", "x",
           "brooklyn.location.ibm-smartcloud.credential", "y", 
           "brooklyn.location.ibm-smartcloud.user", "idcuser");

   public static final Map<String, Object> PROPS = 
           ConfigUtils.filterForPrefixAndStrip(FULL_PROPS, "brooklyn.location.ibm-smartcloud.").asMapWithStringKeys();

   @Test
   public void testIbmSmartCloudLoadsWithProps() {
      ManagementContext managementContext = new LocalManagementContext();
      Location l = managementContext.getLocationRegistry().resolve("ibm-smartcloud", PROPS);
      assertTrue(l instanceof IbmSmartCloudLocation);
      Assert.assertEquals(((IbmSmartCloudLocation)l).getUser(), "idcuser");
      Assert.assertEquals(((IbmSmartCloudLocation)l).getIdentity(), "x");
   }
   
   @Test
   public void testIbmSmartCloudLoadsWithInheritedProps() {
      BrooklynProperties props = BrooklynProperties.Factory.newEmpty();
      props.putAll(FULL_PROPS);
      ManagementContext managementContext = new LocalManagementContext(props);
      Location l = managementContext.getLocationRegistry().resolve("ibm-smartcloud");
      assertTrue(l instanceof IbmSmartCloudLocation);
      Assert.assertEquals(((IbmSmartCloudLocation)l).getUser(), "idcuser");
      Assert.assertEquals(((IbmSmartCloudLocation)l).getIdentity(), "x");
   }
   
   @Test
   public void testIbmSmartCloudSshMachineLocationLoads() {
      ManagementContext managementContext = new LocalManagementContext();
      IbmSmartCloudLocation l = (IbmSmartCloudLocation) managementContext.getLocationRegistry().resolve("ibm-smartcloud", PROPS);
      SshMachineLocation ssh = l.createIbmSmartCloudSshMachineLocation("localhost", "id", "/tmp/path");
      Assert.assertEquals(ssh.getUser(), "idcuser");
   }

}
