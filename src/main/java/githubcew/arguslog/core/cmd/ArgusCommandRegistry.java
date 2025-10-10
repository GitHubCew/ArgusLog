package githubcew.arguslog.core.cmd;

import githubcew.arguslog.config.ArgusConfigurer;
import githubcew.arguslog.core.cmd.code.JadCmd;
import githubcew.arguslog.core.cmd.monitor.LsCmd;
import githubcew.arguslog.core.cmd.monitor.MonitorCmd;
import githubcew.arguslog.core.cmd.monitor.RemoveCmd;
import githubcew.arguslog.core.cmd.spring.IocCmd;
import githubcew.arguslog.core.cmd.sql.SqlCmd;
import githubcew.arguslog.core.cmd.system.*;
import githubcew.arguslog.core.cmd.trace.RevertCmd;
import githubcew.arguslog.core.cmd.trace.TraceCmd;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Argus 内置命令注册中心
 * 负责注册和管理所有系统内置命令
 *
 * @author chenenwei
 */
@Order(-Integer.MAX_VALUE)
@Component
public class ArgusCommandRegistry implements ArgusConfigurer {

    private CommandManager commandManager;

    /**
     * 注册所有内置命令到命令管理器
     *
     * @param commandManager 命令管理器
     */
    @Override
    public void registerCommand(CommandManager commandManager) {
        if (this.commandManager == null) {
            this.commandManager = commandManager;
        }
        // 基础命令
        this.commandManager.register("connect", ConnectCmd.class);
        this.commandManager.register("exit", ExitCmd.class);
        this.commandManager.register("logout", LogoutCmd.class);
        this.commandManager.register("help", HelpCmd.class);
        this.commandManager.register("clear", ClearCmd.class);
        this.commandManager.register("show", ShowCmd.class);
        this.commandManager.register("set", SetCmd.class);
        this.commandManager.register("reset", ResetCmd.class);

        // 监控命令
        this.commandManager.register("ls", LsCmd.class);
        this.commandManager.register("monitor", MonitorCmd.class);
        this.commandManager.register("remove", RemoveCmd.class);

        // 调用链命令
        this.commandManager.register("trace", TraceCmd.class);
        this.commandManager.register("revert", RevertCmd.class);

        // 代码相关命令
        this.commandManager.register("jad", JadCmd.class);

        // spring相关命令
        this.commandManager.register("ioc", IocCmd.class);

        // sql相关命令
        this.commandManager.register("sql", SqlCmd.class);
    }
}