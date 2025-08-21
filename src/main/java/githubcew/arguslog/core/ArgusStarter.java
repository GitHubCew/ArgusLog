package githubcew.arguslog.core;

import githubcew.arguslog.core.auth.Authenticator;
import githubcew.arguslog.core.cmd.CommandManager;
import githubcew.arguslog.core.cmd.ExecuteResult;
import githubcew.arguslog.core.util.CommonUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.List;

/**
 * Argus
 * @author chenenwei
 */
@Component
public class ArgusStarter {

    private final ArgusManager argusManager;

    @Autowired
    public ArgusStarter (ArgusManager argusManager) {
        this.argusManager = argusManager;
    }

    /**
     * 启动
     * @param session session
     * @param inputCmd 输入命令
     * @return 输出结果
     */
    public String start(WebSocketSession session, String inputCmd) {

        ArgusRequest request = argusManager.getExtractor().extract(session, inputCmd);
        ArgusResponse response = new ArgusResponse();
        CommandManager commandManager = argusManager.getCommandManager();

        // 判断命令是否需要鉴权
        if (commandManager.isUnauthorizedCommand(request.getRequestCommand().getCommand())) {
            // 执行命令
            ExecuteResult executeResult = argusManager.getCommandManager().execute(request);
            response.setExecuteResult(executeResult);
            return CommonUtil.formatOutput(response.getToken(), response.getExecuteResult());
        }

        // 认证器
        List<Authenticator> authenticators = argusManager.getAuthenticators();

        // 认证
        boolean anyAuthenticated = false;
        for (Authenticator authenticator : authenticators) {
            if (!authenticator.supports(request)) {
                continue;
            }
            if(authenticator.authenticate(request, response)) {
                // 是否立即返回
                if (authenticator.returnImmediately()) {
                    return CommonUtil.formatOutput(response.getToken(), response.getExecuteResult());
                }
                anyAuthenticated = true;
                break;
            }
        }
        if (!anyAuthenticated) {
            return CommonUtil.formatOutput(null, new ExecuteResult(ArgusConstant.FAILED, "Unauthorized!"));
        }

        // 执行命令
        ExecuteResult executeResult = argusManager.getCommandManager().execute(request);
        response.setExecuteResult(executeResult);
        return CommonUtil.formatOutput(response.getToken(), response.getExecuteResult());
    }
}
