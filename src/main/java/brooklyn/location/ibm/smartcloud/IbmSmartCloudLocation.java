package brooklyn.location.ibm.smartcloud;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.AbstractCloudMachineProvisioningLocation;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.internal.Repeater;
import brooklyn.util.time.Time;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
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

public class IbmSmartCloudLocation extends AbstractCloudMachineProvisioningLocation implements IbmSmartLocationConfig {

   private static final long serialVersionUID = -828289137296787878L;
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

   public IbmSmartCloudSshMachineLocation obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
      String postfix = Integer.toString(new Random().nextInt(Integer.MAX_VALUE));
      String name = name("brooklyn-instance" , postfix);
      String keyName = name("brooklyn-keypair" , postfix);
      String dataCenterID = findLocation(Preconditions.checkNotNull(getLocation(), "location")).getId();
      String imageID = findImage(Preconditions.checkNotNull(getImage(), "image"), dataCenterID).getID();
      String instanceTypeID = checkNotNull(findInstanceType(imageID, getInstanceType()), errorMessage("instanceType")).getId();
      try {
         Key key = getKeyPairOrCreate(keyName);
         String privateKeyPath = storePrivateKeyOnTempFile(keyName, key.getMaterial());
         List<Instance> instances = client.createInstance(name, dataCenterID, imageID, instanceTypeID, keyName, null);
         String serverId = Iterables.getOnlyElement(instances).getID();
         Instance instance = waitForInstance(Instance.Status.ACTIVE, serverId, 
                 getConfig(IbmSmartLocationConfig.CLIENT_POLL_TIMEOUT_MILLIS), 
                 getConfig(IbmSmartLocationConfig.CLIENT_POLL_PERIOD_MILLIS) ); 
         log.info("Using server-supplied private key for "+serverId+" ("+instance.getIP()+"): "+privateKeyPath);
         return registerIbmSmartCloudSshMachineLocation(instance.getIP(), serverId, privateKeyPath);
      } catch (Exception e) {
         throw Throwables.propagate(e);
      }
   }

   public void release(SshMachineLocation machine) {
      try {
         String serverIdMsg = String.format("Server ID for machine(%s) must not be null", machine.getDisplayName());
         String serverId = checkNotNull(serverIds.get(machine), serverIdMsg);
         client.deleteInstance(serverId);
         waitForInstance(Instance.Status.REMOVED, serverId, 
                 getConfig(IbmSmartLocationConfig.CLIENT_POLL_TIMEOUT_MILLIS), 
                 getConfig(IbmSmartLocationConfig.CLIENT_POLL_PERIOD_MILLIS));
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

   private String storePrivateKeyOnTempFile(String keyName, String keyMaterial) throws IOException {
      File privateKey = File.createTempFile(keyName, "_rsa");
      Files.write(keyMaterial, privateKey, Charsets.UTF_8);
      Runtime.getRuntime().exec("chmod 400 " + privateKey.getAbsolutePath());
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

   /** waits for the instance to be listed by the client; it is not necessarily sshable however */
   private Instance waitForInstance(final Instance.Status desiredStatus, final String serverId, long timeoutMillis, long periodMillis) throws Exception {
      boolean isInDesiredStatus = Repeater.create("Wait until the instance is ready")
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
                    log.debug("looking for IBM SCE server "+serverId+", status: "+status.name());
                    return status.equals(desiredStatus) || Instance.Status.FAILED.equals(desiredStatus);
                 }
              })
              .every(periodMillis, TimeUnit.MILLISECONDS)
              .limitTimeTo(timeoutMillis, TimeUnit.MILLISECONDS)
              .run();
      if(!isInDesiredStatus) {
         throw new RuntimeException("Instance is not running");
      }
      return client.describeInstance(serverId);
   }

   protected void waitForSshable(final SshMachineLocation machine, long delayMs) {
       LOG.info("Started VM in {}; waiting {} for it to be sshable on {}@{}",
               new Object[] {
                       setup.getDescription(),
                       Time.makeTimeStringRounded(delayMs),
                       machine.getUser(), machine.getAddress(), 
               });
       
       boolean reachable = new Repeater()
           .repeat()
           .every(1,SECONDS)
           .until(new Callable<Boolean>() {
               public Boolean call() {
                   return machine.isSshable();
               }})
           .limitTimeTo(delayMs, MILLISECONDS)
           .run();

       if (!reachable) {
           throw new IllegalStateException("SSH failed for "+
                   machine.getUser()+"@"+machine.getAddress()+" ("+setup.getDescription()+") after waiting "+
                   Time.makeTimeStringRounded(delayMs));
       }
   }
   
   protected IbmSmartCloudSshMachineLocation registerIbmSmartCloudSshMachineLocation(String ipAddress, String serverId, String privateKeyPath) {
      IbmSmartCloudSshMachineLocation machine = createIbmSmartCloudSshMachineLocation(ipAddress, serverId, privateKeyPath);
      machine.setParent(this);
      
      waitForSshable(machine, getConfig(IbmSmartLocationConfig.SSH_REACHABLE_TIMEOUT_MILLIS));
      
      if (getConfig(IbmSmartLocationConfig.SSHD_SUBSYSTEM_ENABLE)) {
          log.debug(this+": machine "+ipAddress+" is sshable, enabling sshd subsystem section");
          machine.run("sudo sed -i \"s/#Subsystem/Subsystem/\" /etc/ssh/sshd_config");
          machine.run("sudo /etc/init.d/sshd restart");
          // wait 30s for ssh to restart (overkill, but safety first; cloud is so slow it won't matter!)
          Time.sleep(30*1000);
      } else {
          log.debug(this+": machine "+ipAddress+" is not yet sshable");
      }
      
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

   private Location findLocation(final String location) {
      List<Location> locations;
      try {
         locations = client.describeLocations();
      } catch (Exception e) {
         throw Throwables.propagate(e);
      }
      Optional<Location> result = Iterables.tryFind(locations, new Predicate<Location>() {
         public boolean apply(Location input) {
            return input.getName().contains(location);
         }
      });
      if (!result.isPresent()) {
          log.warn("IBM SmartCloud unknown location "+location);
          log.info("IBM SmartCloud locations ("+locations.size()+") are:");
          for (Location l: locations) 
              log.info("  "+l.getName()+" "+l.getLocation()+" "+l.getId());
          throw new NoSuchElementException("Unknown IBM SmartCloud location "+location);
      }
      return result.get();
   }

   private Image findImage(final String imageName, final String dataCenterID) {
      List<Image> images;
      try {
         images = client.describeImages();
      } catch (Exception e) {
         throw Throwables.propagate(e);
      }
      Optional<Image> result = Iterables.tryFind(images, new Predicate<Image>() {
         public boolean apply(Image input) {
            if (!input.getName().contains(imageName)) return false;
            if (!input.getLocation().equals(dataCenterID)) return false;
            return true;
         }
      });
      
      if (!result.isPresent()) {
          log.warn("IBM SmartCloud unknown image "+imageName+" (in location "+dataCenterID+")");
          log.info("IBM SmartCloud images ("+images.size()+") are:");
          for (Image img: images) 
              log.info("  "+img.getName()+" "+img.getLocation()+" "+img.getOwner()+" "+img.getID());
          throw new NoSuchElementException("Unknown IBM SmartCloud image "+imageName);
      }
      return result.get();
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
