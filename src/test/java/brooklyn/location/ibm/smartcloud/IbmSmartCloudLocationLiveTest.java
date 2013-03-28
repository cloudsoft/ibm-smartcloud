package brooklyn.location.ibm.smartcloud;

import com.google.common.collect.ImmutableMap;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static com.google.common.base.Preconditions.checkNotNull;

@Test(groups = {"Live"} )
public class IbmSmartCloudLocationLiveTest {

   private String identity;
   private String credential;

   @BeforeClass
   void init() {
      identity = checkNotNull(System.getProperty("identity"));
      credential = checkNotNull(System.getProperty("credential"));
   }

   @Test
   public void testObtain() throws Exception {
      IbmSmartCloudLocation ibmSmartCloudLocation = new IbmSmartCloudLocation(ImmutableMap.of("identity", identity,
              "credential", credential));
      ibmSmartCloudLocation.obtain(ImmutableMap.of());
   }

   @Test
   public void testRelease() throws Exception {

   }
}
