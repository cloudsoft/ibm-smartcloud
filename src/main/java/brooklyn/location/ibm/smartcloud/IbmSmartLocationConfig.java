package brooklyn.location.ibm.smartcloud;

import brooklyn.config.ConfigKey;
import brooklyn.entity.basic.ConfigKeys;
import brooklyn.location.cloud.CloudLocationConfig;

public interface IbmSmartLocationConfig extends CloudLocationConfig {
   public static final ConfigKey<String> LOCATION = ConfigKeys.newStringConfigKey("location",
           "Override the location configured (default 'Raleigh')", "Raleigh");
   public static final ConfigKey<String> IMAGE = ConfigKeys.newStringConfigKey("image",
           "Override the image configured (default 'Red Hat Enterprise Linux 6.3 (64-bit)')",
           "Red Hat Enterprise Linux 6.3 (64-bit)");
   public static final ConfigKey<String> INSTANCE_TYPE_LABEL = ConfigKeys.newStringConfigKey("instanceType",
           "Override the instanceType configured (default 'Copper')",
           "Copper");
   
   public static final ConfigKey<Integer> MAX_ITERATIONS =
           ConfigKeys.newIntegerConfigKey("maxIterations", "how many ssh loop attempts (default 120 times)", 120);

   public static final ConfigKey<Long> PERIOD =
           ConfigKeys.newLongConfigKey("period", "how long to wait between ssh loop iterations (default 30 seconds)", 30L);
   
}
