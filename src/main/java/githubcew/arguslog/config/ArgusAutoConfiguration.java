package githubcew.arguslog.config;

import githubcew.arguslog.monitor.trace.jdk.JdkProxyWrapper;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;

import java.util.Objects;

/**
 * Argus 主自动配置类。
 * <div>
 *   作为 Argus 日志与监控系统的入口配置，负责自动注册核心组件、扫描基础包路径，
 *   并根据配置属性初始化 JDK 动态代理的排除规则。
 * </div>
 * <div>
 *   本类通过实现 {@link ImportBeanDefinitionRegistrar} 在 Spring 容器启动早期阶段
 *   扫描并注册 Argus 内部组件；同时通过 {@link InitializingBean} 接口在属性绑定完成后
 *   应用用户自定义的代理排除策略。
 * </div>
 * <div>
 *   其他具体功能（如 Web、核心服务等）已拆分至独立的配置类，以保持结构清晰、职责单一。
 * </div>
 *
 * @author chenenwei
 */
@Configuration
@Order
@EnableConfigurationProperties(ArgusProperties.class)
public class ArgusAutoConfiguration implements ImportBeanDefinitionRegistrar, InitializingBean {

    @Autowired
    private ArgusProperties argusProperties;

    /**
     * 在 Spring 容器解析配置类阶段动态注册 Bean 定义。
     * <div>
     *   通过 {@link ClassPathBeanDefinitionScanner} 扫描 Argus 内部包路径 {@code githubcew.arguslog}，
     *   自动发现并注册带有 Spring 注解（如 {@code @Component}、{@code @Service} 等）的类为 Bean。
     * </div>
     * <div>
     *   此机制确保 Argus 的内部组件无需用户手动声明即可被容器管理，提升开箱即用体验。
     * </div>
     *
     * @param importingClassMetadata 当前导入该配置类的元数据（本实现中未使用）
     * @param registry               Bean 定义注册表，用于动态注册组件
     */
    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // 扫描 Argus 全部组件包
        scanPackages(registry, "githubcew.arguslog");
    }

    /**
     * 执行包路径扫描并注册匹配的 Bean 定义。
     * <div>
     *   创建 {@link ClassPathBeanDefinitionScanner} 实例，并对指定的基础包进行组件扫描。
     * </div>
     * <div>
     *   默认使用 Spring 的组件过滤规则（如 {@code @Component} 及其派生注解），
     *   无需额外配置即可识别 Argus 内部的各类服务、处理器和工具类。
     * </div>
     *
     * @param registry     Bean 定义注册表
     * @param basePackages 要扫描的基础包路径数组
     */
    private void scanPackages(BeanDefinitionRegistry registry, String... basePackages) {
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(registry);
        scanner.scan(basePackages);
    }

    /**
     * 在属性绑定完成后执行初始化逻辑。
     * <div>
     *   从 {@link ArgusProperties} 中读取用户配置的 JDK 动态代理排除类和包，
     *   并将其注册到 {@link JdkProxyWrapper.Excluded} 全局排除列表中。
     * </div>
     * <div>
     *   此机制允许用户避免对特定类（如第三方库、性能敏感类）进行代理包装，
     *   防止不必要的性能开销或兼容性问题。
     * </div>
     */
    @Override
    public void afterPropertiesSet() {
        if (!Objects.isNull(argusProperties.getJdkPoxyWrapExcludeClasses())) {
            argusProperties.getJdkPoxyWrapExcludeClasses().forEach(JdkProxyWrapper.Excluded::addExcludeClass);
        }

        if (!Objects.isNull(argusProperties.getJdkProxyWrapExcludePackages())) {
            argusProperties.getJdkProxyWrapExcludePackages().forEach(JdkProxyWrapper.Excluded::addExcludePackage);
        }
    }
}