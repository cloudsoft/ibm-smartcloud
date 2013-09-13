package brooklyn.location.ibm.smartcloud;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.Charsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.location.NoMachinesAvailableException;
import brooklyn.location.basic.SshMachineLocation;
import brooklyn.location.cloud.AbstractCloudMachineProvisioningLocation;
import brooklyn.location.cloud.CloudMachineNamer;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;
import brooklyn.util.exceptions.Exceptions;
import brooklyn.util.internal.Repeater;
import brooklyn.util.net.Urls;
import brooklyn.util.text.Strings;
import brooklyn.util.time.Duration;
import brooklyn.util.time.Time;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.ibm.cloud.api.rest.client.DeveloperCloud;
import com.ibm.cloud.api.rest.client.DeveloperCloudClient;
import com.ibm.cloud.api.rest.client.bean.Image;
import com.ibm.cloud.api.rest.client.bean.Instance;
import com.ibm.cloud.api.rest.client.bean.InstanceType;
import com.ibm.cloud.api.rest.client.bean.Key;
import com.ibm.cloud.api.rest.client.bean.Location;
import com.ibm.cloud.api.rest.client.exception.UnknownKeyException;

public class IbmSmartCloudLocation extends AbstractCloudMachineProvisioningLocation implements IbmSmartCloudConfig {

    public static final Logger LOG = LoggerFactory.getLogger(IbmSmartCloudLocation.class);
    
    private static final long serialVersionUID = -828289137296787878L;

    private final Map<SshMachineLocation, String> serverIds = Maps.newLinkedHashMap();
    private final Map<SshMachineLocation,String> keyPairsByLocation = MutableMap.of();
    private volatile DeveloperCloudClient client;

    public IbmSmartCloudLocation() {
       super(MutableMap.of());
    }

    @Override
    public void init() {
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

    public SshMachineLocation obtain(Map<?, ?> flags) throws NoMachinesAvailableException {
        ConfigBag setup = ConfigBag.newInstanceExtending(getRawLocalConfigBag(), flags);
        String serverName = new CloudMachineNamer(setup).
                // TODO can we go higher?
                lengthMaxPermittedForMachineName(31).
                generateNewMachineUniqueName();
        
        String dataCenterID = findLocation(getLocation()).getId();
        String imageID = findImage(getImage(), dataCenterID).getID();
        String instanceTypeID = findInstanceType(imageID, getInstanceType()).getId();
        
        String keyName = setup.get(IbmSmartCloudConfig.KEYPAIR_NAME);
        if (keyName==null) keyName = serverName;
        try {
            Key key;
            boolean createdKey = false;
            try {
                key = client.describeKey(keyName);
            } catch (UnknownKeyException e) {
                LOG.debug("Creating new keyPair({}) for {}", keyName, serverName);
                key = client.generateKeyPair(keyName);
                createdKey = true;
            }

            String privateKeyPath = storePrivateKeyInTempFile(keyName, key.getMaterial());
            Instance instance = createInstanceWithRetryStrategy(
                    getConfig(IbmSmartCloudConfig.INSTANCE_CREATION_RETRIES), serverName, keyName, dataCenterID,
                    imageID, instanceTypeID);
            LOG.info("Using server-supplied private key for " + instance.getName() + " (" + instance.getIP() + "): "
                    + privateKeyPath);
            SshMachineLocation result = registerIbmSmartCloudSshMachineLocation(instance.getIP(), instance.getID(), privateKeyPath);
            
            if (createdKey)
                keyPairsByLocation.put(result, keyName);
            
            return result;
            
        } catch (Exception e) {
            LOG.error(String.format("Cannot obtain a new machine with serverName(%s), keyName(%s), dataCenterID(%s), " +
            		"imageID(%s), instanceTypeID(%s)", serverName, keyName, dataCenterID, imageID, instanceTypeID), e);
            throw Throwables.propagate(e);
        }
    }

    public void release(SshMachineLocation machine) {
        try {
            String serverIdMsg = String.format("Server ID for machine(%s) must not be null", machine.getDisplayName());
            String serverId = checkNotNull(serverIds.get(machine), serverIdMsg);
            client.deleteInstance(serverId);
            waitForInstance(Instance.Status.REMOVED, serverId,
                    getConfig(IbmSmartCloudConfig.CLIENT_POLL_TIMEOUT_MILLIS),
                    getConfig(IbmSmartCloudConfig.CLIENT_POLL_PERIOD_MILLIS));
        } catch (Exception e) {
            throw Throwables.propagate(e);
        } finally {
            // delete the key if we created it for this machine
            String keyName = keyPairsByLocation.get(machine);
            if (keyName!=null) {
                // get errors if we try to delete too soon; this may help
                Time.sleep(Duration.FIVE_SECONDS);
                try {
                    client.removeKey(keyName);
                } catch (Exception e) {
                    LOG.error("Cannot delete keypair({})", keyName);
                }
            }
        }
    }

    private String storePrivateKeyInTempFile(String keyName, String keyMaterial) throws IOException {
        File privateKey = File.createTempFile(keyName, "_rsa");
        Files.write(keyMaterial, privateKey, Charsets.UTF_8);
        Runtime.getRuntime().exec("chmod 400 " + privateKey.getAbsolutePath());
        privateKey.deleteOnExit();
        return privateKey.getAbsolutePath();
    }

    private Instance createInstanceWithRetryStrategy(int retries, String serverName, String keyName,
            String dataCenterID, String imageID, String instanceTypeID) throws Exception {
        boolean foundInstance = false;
        int failures = 0;
        Instance instance;
        do {
            instance = createInstance(serverName + "_" + failures, dataCenterID, imageID,
                    instanceTypeID, keyName);
            LOG.info("Creation requested for new SCE VM instance: name({}), keyname({}), location({}), id({}), now waiting", 
                    new Object[] { instance.getName(), instance.getKeyName(), 
                    client.describeLocation(instance.getLocation()).getName(), instance.getID() });
            try {
                foundInstance = waitForInstance(Instance.Status.ACTIVE, instance.getID(),
                        getConfig(IbmSmartCloudConfig.CLIENT_POLL_TIMEOUT_MILLIS),
                        getConfig(IbmSmartCloudConfig.CLIENT_POLL_PERIOD_MILLIS));
            } catch (IllegalStateException e) {
                failures++;
                client.deleteInstance(instance.getID());
                // no need to delete keypair - reuse keyName already created before
            }
        } while (!foundInstance  && failures < retries);
        if(!foundInstance) {
            throw new RuntimeException("Instance with serverId(" + instance.getID() + ") is not running");
        }
        return client.describeInstance(instance.getID());
    }
    
    private Instance createInstance(String serverName, String dataCenterID, String imageID, String instanceTypeID, String keyName) {
        try {
            List<Instance> instances = client.createInstance(serverName, dataCenterID, imageID, instanceTypeID, keyName, null);
            return Iterables.getOnlyElement(instances);
        } catch (Exception e) {
            throw Exceptions.propagate(e);
        }
    }

    /**
     * waits for the instance to be listed by the client; it is not necessarily
     * sshable however
     */
    private boolean waitForInstance(final Instance.Status desiredStatus, final String serverId, long timeoutMillis,
            long periodMillis) throws Exception {
        return Repeater.create("Wait until the instance is ready").until(new Callable<Boolean>() {
            public Boolean call() {
                Instance instance;
                try {
                    instance = client.describeInstance(serverId);
                } catch (Exception e) {
                    LOG.warn(String.format("Cannot find instance with serverId(%s) (continuing to wait)", serverId)+": "+e);
                    return false;
                }
                Instance.Status status = instance.getStatus();
                if (Instance.Status.FAILED.equals(desiredStatus)) {
                    LOG.warn(String.format("Instance with serverId(%s) has status=failed (throwing)", serverId));
                    throw new IllegalStateException("Instance " + instance.getName() + " has status=failed");
                }
                LOG.debug("looking for IBM SCE server " + serverId + ", status: " + status.name());
                return status.equals(desiredStatus);
            }
        }).every(periodMillis, TimeUnit.MILLISECONDS).limitTimeTo(timeoutMillis, TimeUnit.MILLISECONDS).run();
    }

    protected void waitForSshable(final SshMachineLocation machine, long delayMs) {
        LOG.info("Started VM {} in {}; waiting {} for it to be sshable on {}@{}", new Object[] { 
                machine.getDisplayName(), this,
                Time.makeTimeStringRounded(delayMs), machine.getUser(), machine.getAddress(), });

        boolean reachable = new Repeater().repeat().every(1, SECONDS).until(new Callable<Boolean>() {
            public Boolean call() {
                return machine.isSshable();
            }
        }).limitTimeTo(delayMs, MILLISECONDS).run();

        if (!reachable) {
            throw new IllegalStateException("SSH failed for " + machine.getUser() + "@" + machine.getAddress() + " ("
                    + getRawLocalConfigBag().getDescription() + ") after waiting " + Time.makeTimeStringRounded(delayMs));
        }
    }

    protected SshMachineLocation registerIbmSmartCloudSshMachineLocation(String ipAddress,
            String serverId, String privateKeyPath) {
        SshMachineLocation machine = createIbmSmartCloudSshMachineLocation(ipAddress, serverId,
                privateKeyPath);
        machine.setParent(this);

        waitForSshable(machine, getConfig(IbmSmartCloudConfig.SSH_REACHABLE_TIMEOUT_MILLIS));

        if (getConfig(IbmSmartCloudConfig.SSHD_SUBSYSTEM_ENABLE)) {
            LOG.debug(this + ": machine " + ipAddress + " is sshable, enabling sshd subsystem section");
            machine.execCommands("enabling sshd subsystem",
                    ImmutableList.of(
                            "sudo sed -i \"s/#Subsystem/Subsystem/\" /etc/ssh/sshd_config",
                            "sudo /etc/init.d/sshd restart",
                            
                            // TODO remove this and use `Apply same securityGroups rules to iptables, if iptables is running on the node`
                            "sudo service iptables stop",
                            "sudo chkconfig iptables off"));
            // wait 30s for ssh to restart (overkill, but safety first; cloud is so slow it won't matter!)
            Time.sleep(30 * 1000L);
        } else {
            LOG.debug(this + ": machine " + ipAddress + " is not yet sshable");
        }
        
        if (getConfig(IbmSmartCloudConfig.INSTALL_LOCAL_AUTHORIZED_KEYS)) {
            try {
                File authKeys = new File(Urls.mergePaths(System.getProperty("user.home"), ".ssh/authorized_keys"));
                if (authKeys.exists()) {
                    String authKeysContents = Files.toString(authKeys, Charset.defaultCharset());
                    String marker = "EOF_"+Strings.makeRandomId(8);
                    machine.execCommands("updating authorized_keys",
                            ImmutableList.of("cat >> ~/.ssh/authorized_keys << "+marker+"\n" +
                                    ""+authKeysContents.trim()+"\n"+
                                    marker+"\n"));
                }
            } catch (IOException e) {
                LOG.warn("Error installing authorized_keys to "+this+": "+e);
            }
        }
        
        // TODO additional security / vulnerability fixes from cloudsoft-ibm-web project (spin / sydney)
        
        // TODO remove this (ip_tables) and use `Apply same securityGroups rules to iptables, if iptables is running on the node`
        if (getConfig(IbmSmartCloudConfig.STOP_IPTABLES)) {
            machine.execCommands("disabling iptables",
                    ImmutableList.of(
                        "sudo service iptables stop",
                        "sudo chkconfig iptables off"));
            Time.sleep(3 * 1000L);
        }
        serverIds.put(machine, serverId);
        return machine;
    }

    protected SshMachineLocation createIbmSmartCloudSshMachineLocation(String ipAddress, String serverId,
            String privateKeyPath) {
        if (LOG.isDebugEnabled())
            LOG.debug("creating IbmSmartCloudSshMachineLocation representation for {}@{}", new Object[] { getUser(),
                  ipAddress, getRawLocalConfigBag().getDescription() });
        
        MutableMap<Object, Object> props = MutableMap.builder().put("serverId", serverId)
                .put("address", ipAddress).put("displayName", ipAddress).put(USER, getUser())
                .put(PRIVATE_KEY_FILE, privateKeyPath).build();
        if (getManagementContext()!=null)
            return getManagementContext().getLocationManager().createLocation(props, SshMachineLocation.class);
        else
            return new SshMachineLocation(props);
    }

    @Override
    public Map<String, Object> getProvisioningFlags(Collection<String> tags) {
        // TODO if we want to support provisioning flags
        if (tags.size() > 0) {
            LOG.debug("Location {}, ignoring provisioning tags {}", this, tags);
        }
        return MutableMap.<String, Object>of();
    }

    private Location findLocation(final String location) {
        Preconditions.checkNotNull(location, "location must not be null");
        List<Location> locations;
        
        int retriesLeft = 10;
        while (true) {
            try {
                locations = client.describeLocations();
                break;
            } catch (Exception e) {
                retriesLeft--;
                LOG.warn("Error reading IBM locations; retries left: "+retriesLeft+" ("+e+")");
                if (retriesLeft<=0)
                    throw Throwables.propagate(e);
            }
        }
        
        Optional<Location> result = Iterables.tryFind(locations, new Predicate<Location>() {
            public boolean apply(Location input) {
                return input.getName().contains(location);
            }
        });
        if (!result.isPresent()) {
            LOG.warn("IBM SmartCloud unknown location " + location);
            LOG.info("IBM SmartCloud locations (" + locations.size() + ") are:");
            for (Location l : locations)
                LOG.info("  " + l.getName() + " " + l.getLocation() + " " + l.getId());
            throw new NoSuchElementException("Unknown IBM SmartCloud location " + location);
        }
        return result.get();
    }

    private Image findImage(final String imageName, final String dataCenterID) {
        Preconditions.checkNotNull(imageName, "image must not be null");
        List<Image> images;
        try {
            images = client.describeImages();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        Optional<Image> result = Iterables.tryFind(images, new Predicate<Image>() {
            public boolean apply(Image input) {
                if (!input.getName().contains(imageName))
                    return false;
                if (!input.getLocation().equals(dataCenterID))
                    return false;
                return true;
            }
        });

        if (!result.isPresent()) {
            LOG.warn("IBM SmartCloud unknown image " + imageName + " (in location " + dataCenterID + ")");
            LOG.info("IBM SmartCloud images (" + images.size() + ") are:");
            for (Image img : images)
                LOG.info("  " + img.getName() + " " + img.getLocation() + " " + img.getOwner() + " " + img.getID());
            throw new NoSuchElementException("Unknown IBM SmartCloud image " + imageName);
        }
        return result.get();
    }

    private InstanceType findInstanceType(String imageId, final String instanceType) {
        List<InstanceType> instanceTypes;
        try {
            instanceTypes = client.describeImage(imageId).getSupportedInstanceTypes();
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }
        Optional<InstanceType> result = Iterables.tryFind(instanceTypes, new Predicate<InstanceType>() {
            public boolean apply(InstanceType input) {
                return input.getLabel().contains(instanceType);
            }
        });
        if (!result.isPresent()) {
            LOG.warn("IBM SmartCloud unknown instanceType " + instanceType);
            LOG.info("IBM SmartCloud instanceTypes (" + instanceTypes.size() + ") are:");
            for (InstanceType i : instanceTypes)
                LOG.info("  " + i.getLabel() + " " + i.getDetail() + " " + i.getId());
            throw new NoSuchElementException("Unknown IBM SmartCloud instanceType " + instanceType);
        }
        return result.get();
    }

}
