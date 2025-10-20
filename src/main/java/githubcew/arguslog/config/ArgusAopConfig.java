package githubcew.arguslog.config;

import githubcew.arguslog.aop.MethodAdvice;
import githubcew.arguslog.aop.MethodPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Argus AOP 配置
 * - 注册切点、通知、Advisor
 */
@Configuration
public class ArgusAopConfig {

    @Bean
    public MethodPointcut pointcut() {
        return new MethodPointcut();
    }

    @Bean
    public MethodAdvice advice() {
        return new MethodAdvice();
    }

    @Bean
    public DefaultPointcutAdvisor defaultPointcutAdvisor(MethodPointcut pointcut, MethodAdvice advice) {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, advice);
        advisor.setOrder(Integer.MAX_VALUE);
        return advisor;
    }
}
