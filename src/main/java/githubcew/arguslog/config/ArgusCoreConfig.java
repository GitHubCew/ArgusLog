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
 * Argus 核心组件配置类。
 * <div>
 *   本类负责注册 Argus 日志与监控系统所需的底层核心 Bean，包括方法参数格式化器、请求信息提取器、
 *   外部通知通道、权限配置器以及用于签名或加密的 RSA 密钥对。
 * </div>
 * <div>
 *   这些组件共同支撑 Argus 的日志采集、上下文提取、安全控制与实时推送能力。
 * </div>
 *
 * @author chenenwei
 */
@Configuration
public class ArgusCoreConfig {

    /**
     * 注册方法参数格式化器。
     * <div>
     *   该 Bean 实现 {@link MethodParamFormatter} 接口，用于将方法调用中的参数对象转换为可读性良好的字符串，
     *   便于在日志中记录方法入参内容。
     * </div>
     * <div>
     *   默认使用 {@link ArgusMethodParamFormatter}，支持对常见类型（如 POJO、集合、数组）进行安全、简洁的序列化。
     * </div>
     *
     * @return 配置完成的参数格式化器实例
     */
    @Bean
    public MethodParamFormatter paramFormatter() {
        return new ArgusMethodParamFormatter();
    }

    /**
     * 注册 HTTP 请求信息提取器。
     * <div>
     *   该 Bean 实现 {@link Extractor} 接口，用于从当前 HTTP 请求上下文中提取关键信息，
     *   如用户标识、IP 地址、请求路径、Header 等，供日志记录或权限校验使用。
     * </div>
     * <div>
     *   默认使用 {@link ArgusRequestExtractor}，适配 Spring Web 环境下的请求上下文。
     * </div>
     *
     * @return 配置完成的请求提取器实例
     */
    @Bean
    public Extractor extractor() {
        return new ArgusRequestExtractor();
    }

    /**
     * 注册外部通知通道。
     * <div>
     *   该 Bean 实现 {@link Outer} 接口，定义日志或监控事件向外推送的通道。
     * </div>
     * <div>
     *   当前实现为 {@link ArgusWebSocketOuter}，通过 WebSocket 将实时日志事件推送给已连接的前端客户端，
     *   适用于调试或监控面板场景。
     * </div>
     *
     * @return 配置完成的外部通知通道实例
     */
    @Bean
    public Outer outer() {
        return new ArgusWebSocketOuter();
    }

    /**
     * 注册权限配置器。
     * <div>
     *   该 Bean 负责初始化和管理 Argus 系统内部的权限控制策略，
     *   例如哪些用户或角色可以访问敏感日志、执行清除操作或订阅实时流。
     * </div>
     * <div>
     *   使用 {@link ArgusPermissionConfigure} 提供默认权限模型，支持后续扩展或覆盖。
     * </div>
     *
     * @return 权限配置器实例
     */
    @Bean
    public ArgusPermissionConfigure argusPermissionConfigure() {
        return new ArgusPermissionConfigure();
    }

    /**
     * 生成并注册 Argus 系统专用的 RSA 密钥对。
     * <div>
     *   该密钥对可用于请求签名、日志防篡改验证或安全通信场景。
     * </div>
     * <div>
     *   使用 2048 位 RSA 算法生成密钥对，兼顾安全性与性能。密钥在应用启动时初始化，
     *   并由 Spring 容器管理其生命周期。
     * </div>
     *
     * @return 生成的 RSA 密钥对
     * @throws NoSuchAlgorithmException 若 JVM 不支持 RSA 算法（理论上不会发生）
     */
    @Bean
    public KeyPair argusKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        return keyPairGenerator.generateKeyPair();
    }
}