package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.core.cmd.BaseCommand;
import picocli.CommandLine;

/**
 * 清屏
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "clear",
        description = "清除控制台",
        mixinStandardHelpOptions = true,
        version = "1.0")
public class ClearCmd extends BaseCommand {
}
