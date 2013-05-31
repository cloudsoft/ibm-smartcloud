package brooklyn.location.ibm.smartcloud;

import java.util.Map;

import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.location.basic.RegistryLocationResolver;
import brooklyn.util.MutableMap;

public class IbmSmartCloudResolver implements RegistryLocationResolver {

   public static final String IBM_SMARTCLOUD = "ibm-smartcloud";

   public String getPrefix() {
      return IBM_SMARTCLOUD;
   }

   public boolean accepts(String spec, LocationRegistry registry) {
      return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
   }

   public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
      return newLocationFromString(spec, registry, registry.getProperties(), locationFlags);
   }

   public Location newLocationFromString(Map properties, String spec) {
      return newLocationFromString(spec, null, properties, new MutableMap());
   }

   protected IbmSmartCloudLocation newLocationFromString(String spec, LocationRegistry registry, Map properties, 
           Map locationFlags) {
      return new IbmSmartCloudLocation(properties);
   }
}
