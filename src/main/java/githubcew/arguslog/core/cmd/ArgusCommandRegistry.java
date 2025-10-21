package githubcew.arguslog.core.cmd;

import githubcew.arguslog.config.ArgusConfigurer;
import githubcew.arguslog.core.cmd.cache.RedisCmd;
import githubcew.arguslog.core.cmd.code.InvokeCmd;
import githubcew.arguslog.core.cmd.code.JadCmd;
import githubcew.arguslog.core.cmd.code.FindCmd;
import githubcew.arguslog.core.cmd.monitor.LsCmd;
import githubcew.arguslog.core.cmd.monitor.MonitorCmd;
import githubcew.arguslog.core.cmd.monitor.RemoveCmd;
import githubcew.arguslog.core.cmd.mq.MqCmd;
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
        this.commandManager.register(ConnectCmd.class);
        this.commandManager.register(ExitCmd.class);
        this.commandManager.register(LogoutCmd.class);
        this.commandManager.register(HelpCmd.class);
        this.commandManager.register(ClearCmd.class);
        this.commandManager.register(ShowCmd.class);
        this.commandManager.register(SetCmd.class);
        this.commandManager.register(ResetCmd.class);
        this.commandManager.register(ResetOtherCmd.class);
        this.commandManager.register(RmUserCmd.class);
        this.commandManager.register(RmOtherCmd.class);
        this.commandManager.register(UserCmd.class);
        this.commandManager.register(RoleCmd.class);

        // 监控命令
        this.commandManager.register(LsCmd.class);
        this.commandManager.register(MonitorCmd.class);
        this.commandManager.register(RemoveCmd.class);

        // 调用链命令
        this.commandManager.register(TraceCmd.class);
        this.commandManager.register(RevertCmd.class);

        // 代码相关命令
        this.commandManager.register(JadCmd.class);
        this.commandManager.register(FindCmd.class);
        this.commandManager.register(InvokeCmd.class);

        // spring相关命令
        this.commandManager.register(IocCmd.class);

        // 中间件命令
        this.commandManager.register(SqlCmd.class);
        this.commandManager.register(RedisCmd.class);
        this.commandManager.register(MqCmd.class);
    }
}