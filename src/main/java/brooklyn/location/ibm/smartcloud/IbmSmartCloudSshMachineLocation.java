package brooklyn.location.ibm.smartcloud;

import java.util.List;
import java.util.Map;

import brooklyn.location.basic.SshMachineLocation;
import brooklyn.util.internal.ssh.ShellTool;

import com.google.common.base.Function;

public class IbmSmartCloudSshMachineLocation extends SshMachineLocation {

   public IbmSmartCloudSshMachineLocation(Map flags) {
      super(flags);
   }

   @Override
   public int run(final Map props, final List<String> commands, final Map env) {
      if (commands == null || commands.isEmpty()) return 0;
      return execSsh(props, new Function<ShellTool, Integer>() {
         public Integer apply(ShellTool ssh) {
            return ssh.execCommands(props, commands, env);
         }
      });
   }
}
