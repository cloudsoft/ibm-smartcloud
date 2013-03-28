package brooklyn.location.ibm.smartcloud;

import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.AbstractCloudMachineProvisioningLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.config.ConfigBag;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.ibm.cloud.api.rest.client.DeveloperCloud;
import com.ibm.cloud.api.rest.client.DeveloperCloudClient;
import com.ibm.cloud.api.rest.client.bean.Image;
import com.ibm.cloud.api.rest.client.bean.Instance;
import com.ibm.cloud.api.rest.client.bean.InstanceType;
import com.ibm.cloud.api.rest.client.bean.Key;
import com.ibm.cloud.api.rest.client.bean.Location;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.ibm.cloud.api.rest.client.exception.UnknownErrorException;
import com.ibm.cloud.api.rest.client.exception.UnknownKeyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

public class IbmSmartCloudLocation extends AbstractCloudMachineProvisioningLocation implements IbmSmartLocationConfig {

   public static final Logger log = LoggerFactory.getLogger(IbmSmartCloudLocation.class);

   public static final String LOCATION = "Raleigh";
   private static final String IMAGE = "Red Hat Enterprise Linux 6.3 (32-bit)";
   private static final String INSTANCE_TYPE_LABEL = "Copper 32 bit";

   private final Map<IbmSmartCloudSshMachineLocation, String> serverIds = Maps.newLinkedHashMap();
   private final DeveloperCloudClient client;
   private final ConfigBag setup;

   /**
    * typically wants at least ACCESS_IDENTITY and ACCESS_CREDENTIAL
    */
   public IbmSmartCloudLocation(Map<?, ?> conf) {
      super(conf);
      setup = ConfigBag.newInstanceExtending(getConfigBag(), conf);
      client = DeveloperCloud.getClient();
      client.setRemoteCredentials(getIdentity(), getCredential());
   }

   public String getIdentity() {
      return getConfig(ACCESS_IDENTITY);
   }

   public String getCredential() {
      return getConfig(ACCESS_CREDENTIAL);
   }

   public String getUser() {
      return getConfig(USER);
   }

   public String getPassword() {
      return getConfig(PASSWORD);
   }

   @Override
   public SshMachineLocation obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
      String name = "brooklyn-ibm-smartcloud-" + Integer.toString(new Random().nextInt(Integer.MAX_VALUE));
      String keyName = "brooklyn-key";
      Location dataCenter = checkNotNull(findLocation(LOCATION), "location must not be null");
      Image image = checkNotNull(findImage(IMAGE), "image must not be null");
      String imageID = image.getID(); // "20025200", Red Hat Enterprise Linux 6.2 (32-bit)(RTP)
      InstanceType instanceType = checkNotNull(findInstanceType(image.getID(), INSTANCE_TYPE_LABEL),
              "instanceType must not be null");

      try {
         String dataCenterID = dataCenter.getId();
         String instanceTypeID = instanceType.getId(); // "COP32.1/2048/60"; // Copper - 32 bit (vCPU: 1,
         // RAM: 2 GiB,
         // Disk: 60 GiB)

         Key key;
         try {
            key = client.describeKey(keyName);
         } catch (UnknownKeyException e) {
            // The function generateKeyPair() will return the private key, as there is no way to
            // retrieve the private key if we need it for ssh access it should be captured and stored here
            key = client.generateKeyPair(keyName);
         }

         List<Instance> instances = client.createInstance(name, dataCenterID, imageID, instanceTypeID, keyName, null);
         String serverId = Iterables.getOnlyElement(instances).getID();
         Instance myInstance = client.describeInstance(serverId);
         Instance.Status lastStatus = null, currentStatus;
         while (Instance.Status.ACTIVE != (currentStatus = myInstance.getStatus())) {
            if (lastStatus != currentStatus) {
               lastStatus = currentStatus;
               System.out.println(lastStatus);
            }
            Thread.sleep(1000 * 10); // sleep 10 seconds
            myInstance = client.describeInstance(serverId);
            // refetch the instance state
         }
         // Instance is now active.
         System.out.println(currentStatus);
         return registerIbmSmartCloudSshMachineLocation(myInstance.getIP(), serverId);
      } catch (Exception e) {
         throw Throwables.propagate(e);
      }
   }

   protected IbmSmartCloudSshMachineLocation registerIbmSmartCloudSshMachineLocation(String ipAddress, String serverId) {
      IbmSmartCloudSshMachineLocation machine = createIbmSmartCloudSshMachineLocation(ipAddress, serverId);
      machine.setParentLocation(this);
      serverIds.put(machine, serverId);
      return machine;
   }

   protected IbmSmartCloudSshMachineLocation createIbmSmartCloudSshMachineLocation(String ipAddress, String serverId) {
      if (LOG.isDebugEnabled())
         LOG.debug("creating EnstratiusSshMachineLocation representation for {}@{} for {} with {}",
                 new Object[]{getUser(), ipAddress, setup.getDescription()});
      return new IbmSmartCloudSshMachineLocation(MutableMap.builder().put("serverId", serverId)
              .put("address", ipAddress)
              .put("displayName", ipAddress)
              .put(USER, getUser())
              .put(PASSWORD, getPassword())
              .build());
   }

   @Override
   public void release(SshMachineLocation machine) {
      /* // We can now perform other actions against the instance such as saving it
      client.saveInstance(instanceID, "Saved Instance", "A description of my saved instance");
      // Note: the system will not delete before the save is done
      // Or deleting the instance
      client.deleteInstance(instanceID);*/
      try {
         String serverIdMsg = String.format("Server ID for machine(%s) must not be null", machine.getName());
         String serverId = checkNotNull(serverIds.get(machine), serverIdMsg);
         //String reason = String.format("Brooklyn released %s", machine.getName());
         //client.saveInstance(serverId, "Saved Instance", reason);
         // Note: the system will not delete before the save is done
         client.deleteInstance(serverId);
      } catch (Exception e) {
         throw Throwables.propagate(e);
      }
   }

   private Location findLocation(final String location) {
      List<Location> locations;
      try {
         locations = client.describeLocations();
      } catch (Exception e) {
         throw Throwables.propagate(e);
      }
      return checkNotNull(Iterables.tryFind(locations, new Predicate<Location>() {
         public boolean apply(Location input) {
            return input.getName().contains(location);
         }
      })).orNull();
   }

   private Image findImage(final String image) {
      List<Image> images;
      try {
         images = client.describeImages();
      } catch (Exception e) {
         throw Throwables.propagate(e);
      }
      return checkNotNull(Iterables.tryFind(images, new Predicate<Image>() {
         public boolean apply(Image input) {
            return input.getName().contains(image);
         }
      })).orNull();
   }

   private InstanceType findInstanceType(String imageId, final String type) {
      List<InstanceType> instanceTypes;
      try {
         instanceTypes = client.describeImage(imageId).getSupportedInstanceTypes();
      } catch (Exception e) {
         throw Throwables.propagate(e);
      }
      return checkNotNull(Iterables.tryFind(instanceTypes, new Predicate<InstanceType>() {
         public boolean apply(InstanceType input) {
            return input.getLabel().contains(type);
         }
      })).orNull();
   }
}
