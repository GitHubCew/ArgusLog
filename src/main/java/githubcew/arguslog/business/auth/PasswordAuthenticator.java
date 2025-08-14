package githubcew.arguslog.business.auth;

import githubcew.arguslog.core.ContextUtil;

/**
 * 密码认证器
 * @author chenenwei
 */
public class PasswordAuthenticator implements Authenticator{

    @Override
    public int order() {
        return 2;
    }

    /**
     * 密码认证
     * @param argusUser 参数
     * @return 认证结果
     */
    @Override
    public boolean authenticate(ArgusUser argusUser) {
        if (null == argusUser.getUsername() || null == argusUser.getPassword()
        || argusUser.getUsername().isEmpty() || argusUser.getPassword().isEmpty()) {
            return false;
        }
        ArgusUser argusUseBean = ContextUtil.getBean(ArgusUser.class);
        boolean isRight = argusUser.getUsername().equals(argusUseBean.getUsername())
                && argusUser.getPassword().equals(argusUseBean.getPassword());
        if (!isRight) {
            throw new RuntimeException("Auth error");
        }
        return true;
    }
}
