package brooklyn.location.ibm.smartcloud;

import brooklyn.location.basic.SshMachineLocation;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertTrue;

@Test(groups = {"Live"} )
public class IbmSmartCloudLocationLiveTest {

   public static final Logger LOG = LoggerFactory.getLogger(IbmSmartCloudLocationLiveTest.class);

   private String identity;
   private String credential;
   private String user;
   private IbmSmartCloudLocation location;

   protected List<IbmSmartCloudSshMachineLocation> machines = Lists.newArrayList();

   @BeforeClass
   void init() {
      identity = checkNotNull(System.getProperty("identity"));
      credential = checkNotNull(System.getProperty("credential"));
      user = checkNotNull(System.getProperty("user"));
      location = new IbmSmartCloudLocation(ImmutableMap.of("identity", identity, "credential",
              credential));
   }

   @Test
   public void testObtain() throws Exception {
      IbmSmartCloudSshMachineLocation machine = location.obtain(ImmutableMap.of("user", user));
      LOG.info("Provisioned vm {}; checking if ssh'able", machine.toString());
      assertTrue(machine.isSshable());
      machines.add(machine);
   }

   @Test
   public void testRelease() throws Exception {
      List<Exception> exceptions = Lists.newArrayList();
      for(SshMachineLocation machine : machines) {
         try {
            location.release(machine);
         } catch (Exception e) {
            exceptions.add(e);
         }
      }

      if (!exceptions.isEmpty()) {
         throw exceptions.get(0);
      }
      machines.clear();
   }
}
