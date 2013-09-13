package brooklyn.location.ibm.smartcloud;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.testng.Assert.assertTrue;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import brooklyn.entity.basic.Entities;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.management.internal.LocalManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.exceptions.CompoundRuntimeException;
import brooklyn.util.time.Time;

import com.google.common.base.Stopwatch;
import com.google.common.collect.Lists;

@Test(groups = { "Live" })
public class IbmSmartCloudLocationLiveTest {

   public static final Logger LOG = LoggerFactory.getLogger(IbmSmartCloudLocationLiveTest.class);

   private IbmSmartCloudLocation location;

   protected List<SshMachineLocation> machines = Lists.newArrayList();

   private LocalManagementContext managementContext;

   @BeforeClass(alwaysRun = true)
   public void init() {
      managementContext = new LocalManagementContext();
      machines = Lists.newArrayList();
      
      location = (IbmSmartCloudLocation) managementContext.getLocationRegistry().resolveIfPossible("named:ibm-sce-test");
      if (location==null) {
          String identity = checkNotNull(System.getProperty("identity"), 
                  "test requires either a named:ibm-sce-test location or system properties to connect");
          String credential = checkNotNull(System.getProperty("credential"));
          String user = checkNotNull(System.getProperty("user"));
          location = managementContext.getLocationManager().createLocation(
                  LocationSpec.create(IbmSmartCloudLocation.class).configure("identity", identity)
                  .configure("credential", credential).configure("user", user));
      }
   }

   @Test
   public void testObtain() throws Exception {
      LOG.info("Provisioning in "+location);
      SshMachineLocation machine = location.obtain(MutableMap.of());
      machines.add(machine);
      LOG.info("Provisioned VM {} in {}; checking if ssh'able", machine, location);
      assertTrue(machine.isSshable());
   }

   @Test(dependsOnMethods="testObtain")
   public void testRelease() throws Exception {
      List<Exception> exceptions = Lists.newArrayList();
      for (SshMachineLocation machine : machines) {
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
   
   @AfterClass(alwaysRun=true)
   public void tearDown() {
      List<Exception> exceptions = Lists.newArrayList();
      for(SshMachineLocation machine : machines) {
         try {
            location.release(machine);
         } catch (Exception e) {
            LOG.error("Error releasing "+machine, e);
            exceptions.add(e);
         }
      }
      machines.clear();
      if (managementContext != null) Entities.destroyAll(managementContext);
      if (!exceptions.isEmpty()) {
         throw new CompoundRuntimeException("Error releasing machine in "+location, exceptions);
      }
   }
   
    public void go(String locationName) throws Exception {
        try {
            managementContext = new LocalManagementContext();
            location = (IbmSmartCloudLocation) managementContext.getLocationRegistry().resolve(locationName);
            Stopwatch watch = new Stopwatch().start();
            testObtain();
            LOG.info(locationName+": obtain took "+Time.makeTimeStringRounded(watch));
            testRelease();
            LOG.info(locationName+": obtain and release took "+Time.makeTimeStringRounded(watch));
        } finally {
            tearDown();
        }
    }

    public static Thread runInThread(final String locationName) {
        Thread t = new Thread() {
            @Override
            public void run() {
                try {
                    new IbmSmartCloudLocationLiveTest().go(locationName);
                } catch (Exception e) {
                    LOG.warn(locationName+": FAILED: "+e, e);
                }
            }
        };
        t.start();
        return t;
    }
    public static void main(String[] args) {
        runInThread("named:ibm-sce-singapore");
        runInThread("named:ibm-sce-germany");
    }
}
