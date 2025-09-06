package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.core.cmd.BaseCommand;
import picocli.CommandLine;

/**
 * 连接argus
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "connect",
        description = "连接argus",
        mixinStandardHelpOptions = true,
        version = "1.0")
public class ConnectCmd extends BaseCommand {
}
