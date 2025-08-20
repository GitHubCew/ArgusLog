package githubcew.arguslog.core.extractor;

import githubcew.arguslog.core.ArgusConstant;
import githubcew.arguslog.core.ArgusRequest;
import githubcew.arguslog.core.account.Account;
import githubcew.arguslog.core.auth.Token;
import githubcew.arguslog.core.cmd.RequestCommand;
import org.springframework.web.socket.WebSocketSession;

import java.util.Arrays;

/**
 * @author chenenwei
 */
public class ArgusRequestExtractor implements Extractor {

    @Override
    public ArgusRequest extract(WebSocketSession session, String inputCmd) {

        ArgusRequest request = new ArgusRequest();
        request.setSession(session);
        RequestCommand requestCommand = request.getRequestCommand();
        Account account = request.getAccount();
        Token token = request.getToken();
        // 提取token
        if (inputCmd.contains(ArgusConstant.TOKEN_SPLIT)) {
            String[] tokenParts = inputCmd.split(ArgusConstant.TOKEN_SPLIT);
            token.setToken(tokenParts[0]);
            if (tokenParts.length > 1) {
                String[] cmdParts = tokenParts[1].split(ArgusConstant.SPACE_PATTERN);
                requestCommand.setCommand(cmdParts[0]);
                requestCommand.setArgs(Arrays.copyOfRange(cmdParts, 1, cmdParts.length));
            }
        }
        else {
            String[] cmdParts = inputCmd.split(ArgusConstant.SPACE_PATTERN);
            requestCommand.setCommand(cmdParts[0]);
            requestCommand.setArgs(Arrays.copyOfRange(cmdParts, 1, cmdParts.length));
        }

        // 提取账户信息, 命令格式为： auth username password time
        if (requestCommand.getCommand().equals("auth") && requestCommand.getArgs().length >= 2) {
            account.setUsername(requestCommand.getArgs()[0]);
            account.setPassword(requestCommand.getArgs()[1]);
        }
        return request;
    }
}
