package githubcew.arguslog.config;

import githubcew.arguslog.core.account.ArgusUserProvider;
import githubcew.arguslog.core.account.UserProvider;
import githubcew.arguslog.web.auth.ArgusAccountAuthenticator;
import githubcew.arguslog.web.auth.ArgusTokenAuthenticator;
import githubcew.arguslog.web.auth.ArgusTokenProvider;
import githubcew.arguslog.web.auth.TokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Argus 认证与授权配置
 * - 用户提供者
 * - Token 提供者
 * - 认证器注册
 */
@Configuration
public class ArgusAuthConfig {

    @Bean
    @ConditionalOnMissingBean(UserProvider.class)
    public UserProvider userProvider(ArgusProperties argusProperties) {
        return new ArgusUserProvider(argusProperties.getUsername(), argusProperties.getPassword());
    }

    @Bean
    @ConditionalOnMissingBean(TokenProvider.class)
    public TokenProvider tokenProvider(ArgusProperties argusProperties) {
        // ⚠️ 改为方法参数注入，避免循环引用
        return new ArgusTokenProvider(argusProperties.getTokenExpireTime());
    }

    @Bean
    @ConditionalOnMissingBean(ArgusAccountAuthenticator.class)
    public ArgusAccountAuthenticator argusAccountAuthenticator() {
        return new ArgusAccountAuthenticator();
    }

    @Bean
    public ArgusTokenAuthenticator argusTokenAuthenticator() {
        return new ArgusTokenAuthenticator();
    }
}
