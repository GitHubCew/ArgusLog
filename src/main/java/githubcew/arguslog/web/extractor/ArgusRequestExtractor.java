package githubcew.arguslog.web.extractor;

import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.common.constant.ArgusConstant;
import githubcew.arguslog.web.ArgusRequest;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cmd.RequestCommand;
import org.springframework.web.socket.WebSocketSession;

import java.util.Arrays;
import java.util.Objects;

/**
 * @author chenenwei
 */
public class ArgusRequestExtractor implements Extractor {

    /**
     * 提取请求
     * @param session session
     * @param inputCmd 输入的命令
     * @return ArgusRequest
     */
    @Override
    public ArgusRequest extract(WebSocketSession session, String inputCmd) {

        ArgusUser argusUser = ArgusCache.getUserBySession(session);
        if (Objects.isNull(argusUser)) {
            argusUser = new ArgusUser();
        }

        // 提取命令
        RequestCommand requestCommand = new RequestCommand();
        if (!Objects.isNull(inputCmd)) {
            String[] cmdParts = inputCmd.split(ArgusConstant.SPACE_PATTERN);
            requestCommand.setCommand(cmdParts[0]);
            requestCommand.setArgs(Arrays.copyOfRange(cmdParts, 1, cmdParts.length));
        }

        return new ArgusRequest(session, requestCommand, argusUser.getAccount(), argusUser.getToken());
    }
}
