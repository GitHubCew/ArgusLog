package githubcew.arguslog;

import githubcew.arguslog.core.ArgusManager;
import githubcew.arguslog.core.cmd.ArgusCommandProcessor;
import githubcew.arguslog.core.cmd.ExecuteResult;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import githubcew.arguslog.web.ArgusRequest;
import githubcew.arguslog.web.ArgusResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

/**
 * Argus
 *
 * @author chenenwei
 */
@Component
public class ArgusStarter {

    private final ArgusManager argusManager;

    /**
     * 构造方法
     * @param argusManager argusManager
     */
    @Autowired
    public ArgusStarter(ArgusManager argusManager) {
        this.argusManager = argusManager;
    }

    /**
     * 启动
     *
     * @param session  session
     * @param inputCmd 输入命令
     * @return 输出结果
     */
    public String start(WebSocketSession session, String inputCmd) {

        ArgusRequest request = argusManager.getExtractor().extract(session, inputCmd);
        ArgusResponse response = new ArgusResponse();

        // 执行命令
        ExecuteResult executeResult = argusManager.getCommandManager().execute(request);
        response.setExecuteResult(executeResult);
        return OutputWrapper.formatOutput(response.getExecuteResult());
    }
}
