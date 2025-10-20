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
 * Argus 主自动配置类
 * - 负责扫描包和代理配置初始化
 * - 其他逻辑拆分到子配置类
 *
 * @author cew
 */
@Configuration
@Order
@EnableConfigurationProperties(ArgusProperties.class)
public class ArgusAutoConfiguration implements ImportBeanDefinitionRegistrar, InitializingBean {

    @Autowired
    private ArgusProperties argusProperties;

    @Override
    public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        // 扫描 Argus 全部组件包
        scanPackages(registry, "githubcew.arguslog");
    }

    private void scanPackages(BeanDefinitionRegistry registry, String... basePackages) {
        ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(registry);
        scanner.scan(basePackages);
    }

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
