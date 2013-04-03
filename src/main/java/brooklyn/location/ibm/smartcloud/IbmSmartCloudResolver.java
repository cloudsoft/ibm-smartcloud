package brooklyn.location.ibm.smartcloud;

import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.location.basic.RegistryLocationResolver;
import brooklyn.util.MutableMap;

import java.util.Map;

public class IbmSmartCloudResolver implements RegistryLocationResolver {

   public static final String IBM_SMARTCLOUD = "ibm-smartcloud";

   @Override
   public String getPrefix() {
      return IBM_SMARTCLOUD;
   }

   @Override
   public boolean accepts(String spec, LocationRegistry registry) {
      return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
   }

   @Override
   public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
      return newLocationFromString(spec, registry, registry.getProperties(), locationFlags);
   }

   @Override
   public Location newLocationFromString(Map properties, String spec) {
      return newLocationFromString(spec, null, properties, new MutableMap());
   }

   protected IbmSmartCloudLocation newLocationFromString(String spec, LocationRegistry registry, Map properties,
                                             Map locationFlags) {
      /*
      EnstratiusSpecParser details = EnstratiusSpecParser.parse(spec, false);
      if (registry!=null) properties.putAll(registry.getProperties());
      properties.putAll(locationFlags);
      properties.put(CLOUD_REGION_ID, details.region);
      properties.put(CLOUD_DATACENTER, details.datacenter);
      */
      return new IbmSmartCloudLocation(properties);
   }
}
