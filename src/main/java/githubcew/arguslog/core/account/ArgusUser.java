package githubcew.arguslog.core.account;

import githubcew.arguslog.web.ArgusRequest;
import githubcew.arguslog.web.auth.Token;
import lombok.Data;
import org.springframework.web.socket.WebSocketSession;

/**
 * @author chenenwei
 */
@Data
public class ArgusUser {

    /**
     * session
     */
    private WebSocketSession session;

    /**
     * 账户
     */
    private Account account;

    /**
     * 凭证
     */
    private Token token;

    public ArgusUser() {}

    public ArgusUser (ArgusRequest request) {
        this.session = request.getSession();
        this.account = request.getAccount();
        this.token = request.getToken();
    }

    public ArgusUser (WebSocketSession session) {
        this.session = session;
    }
}
