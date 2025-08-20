package githubcew.arguslog.core.auth;

import githubcew.arguslog.core.ArgusRequest;
import githubcew.arguslog.core.ArgusResponse;

/**
 * 认证器
 * @author chenenwei
 */
public interface Authenticator {

    /**
     * 认证
     * @param request 请求
     * @return 认证结果
     */
    boolean authenticate(ArgusRequest request, ArgusResponse response);

    /**
     * 是否支持
     * @param request 请求
     * @return 是否支持
     */
    boolean supports (ArgusRequest request);

    /**
     * 立即返回
     * @return 是否立即返回
     */
    default boolean returnImmediately() {return false;};
}
