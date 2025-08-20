package githubcew.arguslog.core;

import githubcew.arguslog.core.account.Account;
import githubcew.arguslog.core.auth.Token;
import githubcew.arguslog.core.cmd.RequestCommand;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

/**
 * @author chenenwei
 */
@Data
public class ArgusRequest {

    /**
     * session
     */
    private WebSocketSession session;

    /**
     * 命令
     */
    private RequestCommand requestCommand;

    /**
     * 账户
     */
    private Account account;

    /**
     * 凭证
     */
    private Token token;

    /**
     * 构造方法
     */
    public ArgusRequest() {
        this.account = new Account();
        this.requestCommand = new RequestCommand();
        this.token = new Token();
    }

    public ArgusRequest(WebSocketSession session, RequestCommand argusCommand, Account account, Token token) {
        this.session = session;
        this.requestCommand = argusCommand;
        this.account = account;
        this.token = token;
    }

}
