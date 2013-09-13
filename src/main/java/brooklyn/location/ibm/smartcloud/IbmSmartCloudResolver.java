package brooklyn.location.ibm.smartcloud;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import brooklyn.config.ConfigUtils;
import brooklyn.location.Location;
import brooklyn.location.LocationRegistry;
import brooklyn.location.LocationResolver;
import brooklyn.location.LocationSpec;
import brooklyn.location.basic.BasicLocationRegistry;
import brooklyn.management.ManagementContext;
import brooklyn.util.collections.MutableMap;
import brooklyn.util.config.ConfigBag;

public class IbmSmartCloudResolver implements LocationResolver {

    private static final Logger log = LoggerFactory.getLogger(IbmSmartCloudResolver.class);
    
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

   protected IbmSmartCloudLocation newLocationFromString(String spec, LocationRegistry registry, Map<?,?> properties, Map<?,?> locationFlags) {
       ConfigBag tmpProperties = ConfigBag.newInstance();
       if (registry!=null) tmpProperties.putAll(registry.getProperties());
       tmpProperties.putAll(properties);
       tmpProperties.putAll(locationFlags);

       ConfigBag filteredProperties = ConfigBag.newInstance();
       filteredProperties.putAll(ConfigUtils.filterForPrefixAndStrip(tmpProperties.getAllConfig(), "brooklyn."+IBM_SMARTCLOUD+"."));
       if (!filteredProperties.isEmpty())
           log.warn("Properties beginning with deprecated syntax 'brooklyn."+IBM_SMARTCLOUD+"' should be renamed as 'brooklyn.location."+IBM_SMARTCLOUD+"': "+filteredProperties);
       filteredProperties.putAll(ConfigUtils.filterForPrefixAndStrip(tmpProperties.getAllConfig(), "brooklyn.location."+IBM_SMARTCLOUD+"."));
       filteredProperties.putAll(locationFlags);
       
       return managementContext.getLocationManager().createLocation(LocationSpec.create(IbmSmartCloudLocation.class).configure(
               filteredProperties.getAllConfig()));
   }

   
   public void init(ManagementContext managementContext) {
       this.managementContext = checkNotNull(managementContext, "managementContext");
   }

}
