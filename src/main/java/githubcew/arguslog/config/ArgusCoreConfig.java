package githubcew.arguslog.config;

import githubcew.arguslog.core.permission.ArgusPermissionConfigure;
import githubcew.arguslog.monitor.formater.ArgusMethodParamFormatter;
import githubcew.arguslog.monitor.formater.MethodParamFormatter;
import githubcew.arguslog.monitor.outer.ArgusWebSocketOuter;
import githubcew.arguslog.monitor.outer.Outer;
import githubcew.arguslog.web.extractor.ArgusRequestExtractor;
import githubcew.arguslog.web.extractor.Extractor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

/**
 * Argus 核心组件配置
 * - 参数格式化器、Extractor、权限系统、KeyPair 等
 */
@Configuration
public class ArgusCoreConfig {

    @Bean
    public MethodParamFormatter paramFormatter() {
        return new ArgusMethodParamFormatter();
    }

    @Bean
    public Extractor extractor() {
        return new ArgusRequestExtractor();
    }

    @Bean
    public Outer outer() {
        return new ArgusWebSocketOuter();
    }

    @Bean
    public ArgusPermissionConfigure argusPermissionConfigure() {
        return new ArgusPermissionConfigure();
    }

    @Bean
    public KeyPair argusKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }
}
