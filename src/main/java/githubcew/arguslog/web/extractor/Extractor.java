package githubcew.arguslog.web.extractor;

import githubcew.arguslog.web.ArgusRequest;
import org.springframework.web.socket.WebSocketSession;

/**
 * @author chenenwei
 */
@FunctionalInterface
public interface Extractor {

    /**
     * 提取用户
     * @param input 输入
     * @return 用户
     */
     ArgusRequest extract (WebSocketSession session, String input);
}
