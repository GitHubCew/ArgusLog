package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.core.cmd.BaseCommand;
import picocli.CommandLine;

/**
 * 登出命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "logout",
        description = "退出登录",
        mixinStandardHelpOptions = true,
        version = "1.0")
public class LogoutCmd extends BaseCommand {
}
