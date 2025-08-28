package githubcew.arguslog.web.socket;

import githubcew.arguslog.core.cache.ArgusCache;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Objects;

/**
 * Argus校验拦截器
 *
 * @author chenenwei
 */
public class ArgusHandshakeInterceptor implements HandshakeInterceptor {
    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        if (request instanceof ServletServerHttpRequest) {
            ServletServerHttpRequest servletRequest = (ServletServerHttpRequest) request;
            HttpServletRequest httpServletRequest = servletRequest.getServletRequest();

            // 从URL参数获取token（
            String token = httpServletRequest.getParameter("argus-token");

            // 如果URL参数没有，尝试从header获取（兼容其他客户端）
            if (token == null || token.isEmpty()) {
                token = httpServletRequest.getHeader("argus-token");
            }
            if (validateToken(token)) {
                attributes.put("argus-token", token);
                return true;
            }
        }
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler wsHandler, Exception exception) {

    }

    /**
     * 校验token
     *
     * @param token token
     * @return true/false
     */
    public boolean validateToken(String token) {
        if (Objects.isNull(token) || token.isEmpty()) {
            return false;
        }
        return ArgusCache.hasToken(token);
    }
}
