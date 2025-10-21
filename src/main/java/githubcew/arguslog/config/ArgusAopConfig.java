package githubcew.arguslog.config;

import githubcew.arguslog.aop.MethodAdvice;
import githubcew.arguslog.aop.MethodPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Argus AOP 配置类。
 * <div>
 *   本类负责注册面向切面编程（AOP）所需的核心组件，包括自定义切点（Pointcut）、通知（Advice）
 *   以及将二者绑定的 Advisor，用于实现方法级别的日志监控、性能追踪或异常捕获。
 * </div>
 * <div>
 *   所有组件以 Spring Bean 形式声明，便于容器管理、测试替换或条件装配。
 * </div>
 *
 * @author chenenwei
 */
@Configuration
public class ArgusAopConfig {

    /**
     * 注册 Argus 自定义切点。
     * <div>
     *   该 Bean 实现 {@link org.springframework.aop.Pointcut} 接口（通过 {@link MethodPointcut}），
     *   定义哪些方法需要被 AOP 代理拦截（例如带有特定注解、位于特定包路径或满足命名规则的方法）。
     * </div>
     * <div>
     *   切点逻辑封装在 {@link MethodPointcut} 中，支持灵活扩展以适配不同监控策略。
     * </div>
     *
     * @return 方法切点实例
     */
    @Bean
    public MethodPointcut pointcut() {
        return new MethodPointcut();
    }

    /**
     * 注册 Argus 方法通知。
     * <div>
     *   该 Bean 实现环绕通知（Around Advice）逻辑，定义在匹配切点的方法执行前后应执行的操作，
     *   例如记录方法入参、返回值、执行耗时、异常信息等。
     * </div>
     * <div>
     *   通知逻辑由 {@link MethodAdvice} 实现，与切点解耦，便于独立测试和复用。
     * </div>
     *
     * @return 方法通知实例
     */
    @Bean
    public MethodAdvice advice() {
        return new MethodAdvice();
    }

    /**
     * 注册 AOP Advisor，将切点与通知绑定。
     * <div>
     *   使用 {@link DefaultPointcutAdvisor} 将 {@link MethodPointcut} 和 {@link MethodAdvice} 组合，
     *   形成完整的切面（Aspect），供 Spring AOP 代理机制使用。
     * </div>
     * <div>
     *   执行顺序设为 {@code Integer.MAX_VALUE}，确保该 Advisor 在所有其他 AOP 逻辑之后执行，
     *   避免干扰事务、安全等高优先级切面，同时保证日志记录覆盖最终结果（包括异常）。
     * </div>
     *
     * @param pointcut 切点 Bean
     * @param advice   通知 Bean
     * @return 配置完成的 Advisor 实例
     */
    @Bean
    public DefaultPointcutAdvisor argusPointcutAdvisor(MethodPointcut pointcut, MethodAdvice advice) {
        DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, advice);
        advisor.setOrder(Integer.MAX_VALUE);
        return advisor;
    }
}