package brooklyn.location.ibm.smartcloud;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.internal.ssh.SshTool;
import com.google.common.base.Function;
import java.util.List;
import java.util.Map;

public class IbmSmartCloudSshMachineLocation extends SshMachineLocation {

   public IbmSmartCloudSshMachineLocation(Map flags) {
      super(flags);
   }

   @Override
   public int run(final Map props, final List<String> commands, final Map env) {
      if (commands == null || commands.isEmpty()) return 0;
      return execSsh(props, new Function<SshTool, Integer>() {
         public Integer apply(SshTool ssh) {
            return ssh.execCommands(props, commands, env);
         }
      });
   }
}
