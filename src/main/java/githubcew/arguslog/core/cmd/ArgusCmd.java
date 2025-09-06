package githubcew.arguslog.core.cmd;

import picocli.CommandLine;

/**
 * Argus 命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "argus",
        description = "argus命令行工具",
        mixinStandardHelpOptions = true)
public class ArgusCmd extends BaseCommand{
}
