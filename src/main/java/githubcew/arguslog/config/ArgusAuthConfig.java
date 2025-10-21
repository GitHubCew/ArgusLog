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
 * Argus 认证与授权配置类。
 * <div>
 *   本类负责注册 Argus 系统所需的认证核心组件，包括用户信息提供者、Token 生成与验证机制，
 *   以及基于账号密码和 Token 的双重认证器。
 * </div>
 * <div>
 *   所有关键接口均使用 {@link ConditionalOnMissingBean} 注解，允许用户通过自定义实现轻松覆盖默认行为，
 *   提升系统的可扩展性与集成灵活性。
 * </div>
 *
 * @author chenenwei
 */
@Configuration
public class ArgusAuthConfig {

    /**
     * 注册默认的用户信息提供者。
     * <div>
     *   该 Bean 实现 {@link UserProvider} 接口，用于在认证过程中验证用户身份。
     *   默认使用 {@link ArgusUserProvider}，从 {@link ArgusProperties} 中读取预设的用户名和密码进行简单校验。
     * </div>
     * <div>
     *   仅在 Spring 容器中不存在其他 {@code UserProvider} 实现时生效（{@link ConditionalOnMissingBean}），
     *   用户可提供自己的实现（如对接 LDAP、数据库或 OAuth2）以替代默认逻辑。
     * </div>
     *
     * @param argusProperties Argus 配置属性，用于获取预设的登录凭证
     * @return 默认用户提供者实例
     */
    @Bean
    @ConditionalOnMissingBean(UserProvider.class)
    public UserProvider userProvider(ArgusProperties argusProperties) {
        return new ArgusUserProvider(argusProperties.getUsername(), argusProperties.getPassword());
    }

    /**
     * 注册默认的 Token 提供者。
     * <div>
     *   该 Bean 实现 {@link TokenProvider} 接口，负责生成和解析用于会话管理的认证 Token。
     *   默认使用 {@link ArgusTokenProvider}，基于 自定义生成有时效性的令牌。
     * </div>
     * <div>
     *   Token 有效期由 {@link ArgusProperties getTokenExpireTime()} 配置。
     *   同样受 {@link ConditionalOnMissingBean} 保护，允许用户替换为自定义 Token 策略（如 Redis 存储、自定义签名算法等）。
     * </div>
     *
     * @param argusProperties Argus 配置属性，用于获取 Token 过期时间
     * @return 默认 Token 提供者实例
     */
    @Bean
    @ConditionalOnMissingBean(TokenProvider.class)
    public TokenProvider tokenProvider(ArgusProperties argusProperties) {
        return new ArgusTokenProvider(argusProperties.getTokenExpireTime());
    }

    /**
     * 注册基于账号密码的认证器。
     * <div>
     *   该 Bean 负责处理登录请求中的用户名和密码认证流程，依赖 {@link UserProvider} 进行凭证校验。
     *   使用 {@link ArgusAccountAuthenticator} 实现标准的账号密码认证逻辑。
     * </div>
     * <div>
     *   仅在容器中无其他 {@link ArgusAccountAuthenticator} 实现时注册，支持用户扩展认证方式（如多因素认证）。
     * </div>
     *
     * @return 账号密码认证器实例
     */
    @Bean
    @ConditionalOnMissingBean(ArgusAccountAuthenticator.class)
    public ArgusAccountAuthenticator argusAccountAuthenticator() {
        return new ArgusAccountAuthenticator();
    }

    /**
     * 注册基于 Token 的认证器。
     * <div>
     *   该 Bean 用于验证 HTTP 请求中携带的认证 Token（通常在 Header 中），
     *   并将认证结果绑定到当前安全上下文。
     * </div>
     * <div>
     *   使用 {@link ArgusTokenAuthenticator} 实现，依赖 {@link TokenProvider} 解析和校验 Token 有效性。
     *   此 Bean 未使用 {@code @ConditionalOnMissingBean}，表明其为 Argus 必需组件，通常不应被完全替换。
     * </div>
     *
     * @return Token 认证器实例
     */
    @Bean
    public ArgusTokenAuthenticator argusTokenAuthenticator() {
        return new ArgusTokenAuthenticator();
    }
}