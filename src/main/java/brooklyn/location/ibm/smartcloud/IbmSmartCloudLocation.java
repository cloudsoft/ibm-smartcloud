package brooklyn.location.ibm.smartcloud;

import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.AbstractCloudMachineProvisioningLocation;
import brooklyn.util.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.Repeater;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.ibm.cloud.api.rest.client.DeveloperCloud;
import com.ibm.cloud.api.rest.client.DeveloperCloudClient;
import com.ibm.cloud.api.rest.client.bean.Image;
import com.ibm.cloud.api.rest.client.bean.Instance;
import com.ibm.cloud.api.rest.client.bean.InstanceType;
import com.ibm.cloud.api.rest.client.bean.Key;
import com.ibm.cloud.api.rest.client.bean.Location;
import com.ibm.cloud.api.rest.client.exception.KeyExistsException;
import com.ibm.cloud.api.rest.client.exception.KeyGenerationFailedException;
import com.ibm.cloud.api.rest.client.exception.UnauthorizedUserException;
import com.ibm.cloud.api.rest.client.exception.UnknownErrorException;
import com.ibm.cloud.api.rest.client.exception.UnknownKeyException;
import org.apache.commons.io.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;

public class IbmSmartCloudLocation extends AbstractCloudMachineProvisioningLocation implements IbmSmartLocationConfig {

   public static final Logger log = LoggerFactory.getLogger(IbmSmartCloudLocation.class);

   private final Map<IbmSmartCloudSshMachineLocation, String> serverIds = Maps.newLinkedHashMap();
   private final List<String> keyPairNames = Lists.newArrayList();
   private final DeveloperCloudClient client;
   private final ConfigBag setup;

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

   public String getLocation() {
      return getConfig(LOCATION);
   }

   public String getImage() {
      return getConfig(IMAGE);
   }

   public String getInstanceType() {
      return getConfig(INSTANCE_TYPE_LABEL);
   }

   public Long getPeriod() {
      return getConfig(PERIOD);
   }

   public Integer getMaxIterations() {
      return getConfig(MAX_ITERATIONS);
   }

   @Override
   public IbmSmartCloudSshMachineLocation obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
      String postfix = Integer.toString(new Random().nextInt(Integer.MAX_VALUE));
      String name = name("brooklyn-instance" , postfix);
      String keyName = name("brooklyn-keypair" , postfix);
      String dataCenterID = checkNotNull(findLocation(getLocation()), errorMessage("location")).getId();
      String imageID = checkNotNull(findImage(getImage()), errorMessage("image")).getID();
      String instanceTypeID = checkNotNull(findInstanceType(imageID, getInstanceType()), errorMessage("instanceType"))
              .getId();
      try {
         Key key = getKeyPairOrCreate(keyName);
         String privateKeyPath = storePrivateKeyOnTempFile(keyName, key.getMaterial());
         List<Instance> instances = client.createInstance(name, dataCenterID, imageID, instanceTypeID, keyName, null);
         String serverId = Iterables.getOnlyElement(instances).getID();
         Instance instance = waitForInstanceRunning(serverId, getPeriod(), TimeUnit.SECONDS, getMaxIterations());
         return registerIbmSmartCloudSshMachineLocation(instance.getIP(), serverId, privateKeyPath);
      } catch (Exception e) {
         throw Throwables.propagate(e);
      }
   }

   private String storePrivateKeyOnTempFile(String keyName, String keyMaterial) throws IOException {
      File privateKey = File.createTempFile(keyName, "_rsa");
      Files.write(keyMaterial, privateKey, Charsets.UTF_8);
      privateKey.setReadable(true, true);
      privateKey.deleteOnExit();
      keyPairNames.add(keyName);
      return privateKey.getAbsolutePath();
   }

   private Key getKeyPairOrCreate(String keyName) throws IOException, UnauthorizedUserException, UnknownErrorException,
           KeyExistsException, KeyGenerationFailedException {
      try {
         return client.describeKey(keyName);
      } catch (UnknownKeyException e) {
         return client.generateKeyPair(keyName);
      }
   }

   private Instance waitForInstanceRunning(final String serverId, long duration, TimeUnit timeUnit, int maxIterations)
           throws Exception {
      boolean isRunning = Repeater.create("Wait until the instance is ready")
              .until(new Callable<Boolean>() {
                 public Boolean call() {
                    Instance instance;
                    try {
                       instance = client.describeInstance(serverId);
                    } catch (Exception e) {
                       log.error(String.format("Cannot find instance with serverId(%s)", serverId), e);
                       return false;
                    }
                    Instance.Status status = instance.getStatus();
                    return Instance.Status.ACTIVE.equals(status) || Instance.Status.FAILED.equals(status);
                 }
              })
              .every(duration, timeUnit)
              .limitIterationsTo(maxIterations)
              .run();
      if(!isRunning) {
         throw new RuntimeException("Instance is not running");
      }
      return client.describeInstance(serverId);
   }

   protected IbmSmartCloudSshMachineLocation registerIbmSmartCloudSshMachineLocation(String ipAddress,
                                                                                     String serverId,
                                                                                     String privateKeyPath) {
      IbmSmartCloudSshMachineLocation machine = createIbmSmartCloudSshMachineLocation(ipAddress, serverId,
              privateKeyPath);
      machine.setParentLocation(this);
      serverIds.put(machine, serverId);
      return machine;
   }

   protected IbmSmartCloudSshMachineLocation createIbmSmartCloudSshMachineLocation(String ipAddress, String serverId,
                                                                                   String privateKeyPath) {
      if (LOG.isDebugEnabled())
         LOG.debug("creating EnstratiusSshMachineLocation representation for {}@{} for {} with {}",
                 new Object[]{getUser(), ipAddress, setup.getDescription()});
      return new IbmSmartCloudSshMachineLocation(MutableMap.builder().put("serverId", serverId)
              .put("address", ipAddress)
              .put("displayName", ipAddress)
              .put(USER, getUser())
              .put(PRIVATE_KEY_FILE, privateKeyPath)
              .build());
   }

   @Override
   public void release(SshMachineLocation machine) {
      try {
         String serverIdMsg = String.format("Server ID for machine(%s) must not be null", machine.getName());
         String serverId = checkNotNull(serverIds.get(machine), serverIdMsg);
         client.deleteInstance(serverId);
      } catch (Exception e) {
         throw Throwables.propagate(e);
      } finally {
         for(String keyName : keyPairNames) {
            try {
               client.removeKey(keyName);
            } catch (Exception e) {
               LOG.error("Cannot delete keypair({})", keyName);
               throw Throwables.propagate(e);
            }
         }
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

   private String name(String prefix, String postfix) {
      return String.format("%s-%s", prefix, postfix);
   }

   private String errorMessage(String entity) {
      return String.format("%s must not be null" , entity);
   }
}
