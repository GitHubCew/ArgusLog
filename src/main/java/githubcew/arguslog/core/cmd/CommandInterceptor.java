package githubcew.arguslog.core.cmd;

import githubcew.arguslog.core.ArgusRequest;

/**
 * 命令拦截器
 * @author chenenwei
 */
@FunctionalInterface
public interface CommandInterceptor {

    /**
     * 命令拦截器
     * @param request 请求
     * @return 拦截结果
     */
    public boolean intercept (ArgusRequest request);
}
