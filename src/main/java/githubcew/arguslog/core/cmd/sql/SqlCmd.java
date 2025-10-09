package githubcew.arguslog.core.cmd.sql;

import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.MonitorInfo;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.util.List;

/**
 * sql 命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "sql",
        description = "sql 命令",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class SqlCmd extends BaseCommand {

    @CommandLine.Option(
            names = {"-t", "--threshold"},
            description = "sql 输出耗时阈值（ms）"
    )
    private Long threshold;

    @CommandLine.Option(
            names = {"--clear"},
            description = "清除sql监听",
            arity = "0",
            fallbackValue = "true"
    )
    private boolean clear ;

    @CommandLine.Option(
            names = {"-p", "--packageName"},
            description = "过滤的包名",
            arity = "0..1"
    )

    private String packageName;

    @CommandLine.Option(
            names = {"-c", "--className"},
            description = "过滤的类名",
            arity = "0..1"
    )
    private String className;

    @CommandLine.Option(
            names = {"-m", "--methodName"},
            description = "过滤的类名",
            arity = "0..*"
    )
    private List<String> methodNames;

    @Override
    protected Integer execute() throws Exception {

        if (clear) {
            removeSqlMonitor();
        } else {
            addSqlMonitor();
        }
        return OK_CODE;
    }

    private void addSqlMonitor () {
        ArgusCache.addUserSqlMonitor(ArgusUserContext.getCurrentUsername(), new MonitorInfo.Sql(threshold, packageName, className, methodNames));
    }

    private void removeSqlMonitor () {
        ArgusCache.removeUserSqlMonitor(ArgusUserContext.getCurrentUsername());
    }
}
