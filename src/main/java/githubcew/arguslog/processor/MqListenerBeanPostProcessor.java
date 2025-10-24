package githubcew.arguslog.processor;

import githubcew.arguslog.core.cache.ArgusCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQ 监听方法注册器（无代理、无运行时拦截）。
 * <p>
 * 在 Spring 容器初始化 Bean 后，自动扫描所有方法，识别带有 MQ 监听注解（如
 * {@code @RabbitListener}、{@code @KafkaListener}、{@code @RocketMQMessageListener}）
 * 的方法，并将其与对应的队列/主题名称注册到 {@link ArgusCache} 中，供后续监控使用。
 * </p>
 * <p>
 * 特点：
 * <ul>
 *   <li>编译期不依赖任何 MQ 实现库；</li>
 *   <li>运行时自动检测 classpath 中存在的 MQ 注解；</li>
 *   <li>仅在应用启动时执行一次扫描，运行时零开销；</li>
 *   <li>支持多队列/主题配置（如 queues = {"q1", "q2"}）。</li>
 * </ul>
 *
 * @author chenenwei
 * @since 1.0.0
 */
@Component
public class MqListenerBeanPostProcessor implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(MqListenerBeanPostProcessor.class);

    private static final String RABBIT_LISTENER_CLASS = "org.springframework.amqp.rabbit.annotation.RabbitListener";
    private static final String KAFKA_LISTENER_CLASS = "org.springframework.kafka.annotation.KafkaListener";
    private static final String ROCKETMQ_LISTENER_CLASS = "org.apache.rocketmq.spring.annotation.RocketMQMessageListener";

    private static final Map<String, Boolean> CLASS_AVAILABLE_CACHE = new ConcurrentHashMap<>();

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Class<?> beanClass = bean.getClass();

        // 扫描并注册 MQ 监听方法
        scanAndRegisterMqListeners(beanClass);
        return bean; // 不代理，直接返回原对象
    }

    /**
     * 扫描类中所有方法，识别 MQ 监听方法并注册到 ArgusCache。
     */
    private void scanAndRegisterMqListeners(Class<?> clazz) {
        Method[] methods = ReflectionUtils.getAllDeclaredMethods(clazz);

        for (Method method : methods) {
            for (Annotation annotation : method.getAnnotations()) {
                String annClassName = annotation.annotationType().getName();
                if (isSupportedMqListener(annClassName)) {
                    List<String> queueNames = extractQueueNames(annClassName, annotation);
                    if (!queueNames.isEmpty()) {
                        ArgusCache.addMqMethod(method, queueNames);
                        log.debug("Registered MQ listener method: {}.{} -> queues: {}",
                                clazz.getSimpleName(), method.getName(), queueNames);
                    }
                    break;
                }
            }
        }
    }

    /**
     * 判断给定注解类名是否为支持的 MQ 监听注解。
     */
    private boolean isSupportedMqListener(String annotationClassName) {
        return isClassAvailable(RABBIT_LISTENER_CLASS) && annotationClassName.equals(RABBIT_LISTENER_CLASS)
                || isClassAvailable(KAFKA_LISTENER_CLASS) && annotationClassName.equals(KAFKA_LISTENER_CLASS)
                || isClassAvailable(ROCKETMQ_LISTENER_CLASS) && annotationClassName.equals(ROCKETMQ_LISTENER_CLASS);
    }

    /**
     * 检查指定类是否在当前 classpath 中可用。
     */
    private boolean isClassAvailable(String className) {
        return CLASS_AVAILABLE_CACHE.computeIfAbsent(className, name -> {
            try {
                Class.forName(name, false, Thread.currentThread().getContextClassLoader());
                return true;
            } catch (ClassNotFoundException e) {
                return false;
            }
        });
    }

    /**
     * 从 MQ 监听注解中提取队列或主题名称列表。
     */
    private List<String> extractQueueNames(String annClassName, Annotation annotation) {
        try {
            switch (annClassName) {
                case RABBIT_LISTENER_CLASS:
                    Object queues = getAnnotationValue(annotation, "queues");
                    if (queues instanceof String[]) {
                        return Arrays.asList((String[]) queues);
                    }
                    Object value = getAnnotationValue(annotation, "value");
                    if (value instanceof String[]) {
                        return Arrays.asList((String[]) value);
                    }
                    break;

                case KAFKA_LISTENER_CLASS:
                    Object topics = getAnnotationValue(annotation, "topics");
                    if (topics instanceof String[]) {
                        return Arrays.asList((String[]) topics);
                    }
                    break;

                case ROCKETMQ_LISTENER_CLASS:
                    Object topic = getAnnotationValue(annotation, "topic");
                    if (topic instanceof String) {
                        return Collections.singletonList((String) topic);
                    }
                    break;
            }
        } catch (Exception e) {
            log.error("Failed to extract queue/topic names from annotation: {}", annClassName, e);
        }
        return Collections.emptyList();
    }

    /**
     * 通过反射获取注解属性值。
     */
    private Object getAnnotationValue(Annotation annotation, String attributeName) throws Exception {
        Method method = annotation.annotationType().getMethod(attributeName);
        return method.invoke(annotation);
    }
}