package brooklyn.location.ibm.smartcloud;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;

import com.google.common.collect.Maps;

public class IbmSmartCloudResolver implements LocationResolver {

   public static final String IBM_SMARTCLOUD = "ibm-smartcloud";

   private ManagementContext managementContext;

   public String getPrefix() {
      return IBM_SMARTCLOUD;
   }

   public boolean accepts(String spec, LocationRegistry registry) {
      return BasicLocationRegistry.isResolverPrefixForSpec(this, spec, true);
   }

   @SuppressWarnings("rawtypes")
   public Location newLocationFromString(Map locationFlags, String spec, LocationRegistry registry) {
      return newLocationFromString(spec, registry, registry.getProperties(), locationFlags);
   }

   @SuppressWarnings("rawtypes")
   public Location newLocationFromString(Map properties, String spec) {
      return newLocationFromString(spec, null, properties, new MutableMap());
   }

   @SuppressWarnings("unchecked")
   protected IbmSmartCloudLocation newLocationFromString(String spec, LocationRegistry registry, Map<?,?> properties, Map<?,?> locationFlags) {
       Map<Object,Object> tmpProperties = Maps.newLinkedHashMap();
       if (registry!=null) tmpProperties.putAll(registry.getProperties());
       tmpProperties.putAll(properties);
       tmpProperties.putAll(locationFlags);
       return managementContext.getLocationManager().createLocation(LocationSpec.create(IbmSmartCloudLocation.class).configure(tmpProperties));
   }

   
   public void init(ManagementContext managementContext) {
       this.managementContext = checkNotNull(managementContext, "managementContext");
   }

}
