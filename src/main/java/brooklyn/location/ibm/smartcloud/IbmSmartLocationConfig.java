package brooklyn.location.ibm.smartcloud;

import brooklyn.config.ConfigKey;
import brooklyn.event.basic.BasicConfigKey;
import brooklyn.event.basic.BasicConfigKey.StringConfigKey;
import brooklyn.location.cloud.CloudLocationConfig;

public interface IbmSmartLocationConfig extends CloudLocationConfig {
   public static final ConfigKey<String> LOCATION = new StringConfigKey("location",
           "Override the location configured (default 'Raleigh')", "Raleigh");
   public static final ConfigKey<String> IMAGE = new StringConfigKey("image",
           "Override the image configured (default 'Red Hat Enterprise Linux 6.3 (64-bit)')",
           "Red Hat Enterprise Linux 6.3 (64-bit)");
   public static final ConfigKey<String> INSTANCE_TYPE_LABEL = new StringConfigKey("instanceType",
           "Override the instanceType configured (default 'Copper')",
           "Copper");
   
   public static final BasicConfigKey<Integer> MAX_ITERATIONS =
           new BasicConfigKey<Integer>(Integer.class, "maxIterations", "how many ssh loop attempts (default 120 times)", 120);

   
   public static final BasicConfigKey<Long> PERIOD =
           new BasicConfigKey<Long>(Long.class, "period", "how long to wait between ssh loop iterations (default 30 seconds)", 30L);

   
//   public static final ConfigKey<Integer> PERIOD = new BasicConfigKey<Integer>(Integer.class,
//           "period",
//           , 30);
}
