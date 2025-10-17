package githubcew.arguslog.core.cmd.mq;

import githubcew.arguslog.common.util.ContextUtil;
import org.springframework.context.ApplicationContext;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * 多消息队列（Multi-MQ）消费者管理器。
 * <p>
 * 支持自动识别并管理以下消息中间件的消费者：
 * <ul>
 *   <li>RabbitMQ</li>
 *   <li>RocketMQ</li>
 *   <li>Kafka</li>
 * </ul>
 * <p>
 * 提供以下功能：
 * <ul>
 *   <li>全局或按队列/Topic 粒度启停消费者</li>
 *   <li>零依赖具体 MQ 实现（通过反射和类存在性判断）</li>
 *   <li>安全反射调用，异常静默处理</li>
 * </ul>
 *
 * @author chenenwei
 */
public class MqConsumeManager {

    private final ApplicationContext context;

    /**
     * 构造函数，从 {@link ContextUtil} 获取 Spring 应用上下文。
     */
    public MqConsumeManager() {
        this.context = ContextUtil.context();
    }

    // ===================================================================
    //                        MQ 类型探测逻辑
    // ===================================================================

    /**
     * 判断指定类是否存在于当前类路径中。
     *
     * @param className 完整类名
     * @return 若类存在返回 {@code true}，否则返回 {@code false}
     */
    private boolean hasClass(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 检测当前环境是否集成了 RabbitMQ。
     *
     * @return 若检测到 RabbitMQ 相关类则返回 {@code true}
     */
    public boolean isRabbitMQ() {
        return hasClass("org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry");
    }

    /**
     * 检测当前环境是否集成了 RocketMQ。
     *
     * @return 若检测到 RocketMQ 相关类则返回 {@code true}
     */
    public boolean isRocketMQ() {
        return hasClass("org.apache.rocketmq.spring.core.RocketMQListener")
                || hasClass("org.apache.rocketmq.spring.core.RocketMQTemplate");
    }

    /**
     * 检测当前环境是否集成了 Kafka。
     *
     * @return 若检测到 Kafka 相关类则返回 {@code true}
     */
    public boolean isKafka() {
        return hasClass("org.springframework.kafka.core.KafkaTemplate")
                || hasClass("org.springframework.kafka.annotation.KafkaListener");
    }

    // ===================================================================
    //                        通用反射工具方法
    // ===================================================================

    /**
     * 安全地调用指定对象的无参方法，忽略所有异常。
     *
     * @param obj        目标对象
     * @param methodName 方法名
     */
    private void safeInvoke(Object obj, String methodName) {
        if (obj == null || methodName == null) {
            return;
        }
        try {
            Method method = obj.getClass().getMethod(methodName);
            method.invoke(obj);
        } catch (Exception ignored) {
            // 静默忽略反射异常
        }
    }

    /**
     * 安全获取 RabbitMQ 的监听容器集合。
     *
     * @param registry RabbitListenerEndpointRegistry 实例
     * @return 容器集合，若失败则返回空集合
     */
    private Collection<?> safeGetRabbitContainers(Object registry) {
        if (registry == null) {
            return Collections.emptyList();
        }
        try {
            Method method = registry.getClass().getMethod("getListenerContainers");
            Object result = method.invoke(registry);
            if (result instanceof Collection) {
                return (Collection<?>) result;
            }
        } catch (Exception ignored) {
            // 静默忽略反射异常
        }
        return Collections.emptyList();
    }

    // ===================================================================
    //                        RabbitMQ 控制逻辑
    // ===================================================================

    /**
     * 获取 RabbitMQ 的监听注册中心实例。
     *
     * @return 注册中心实例，若不可用则返回 {@code null}
     */
    private Object getRabbitRegistry() {
        if (!isRabbitMQ()) {
            return null;
        }
        try {
            return context.getBean(Class.forName("org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry"));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 停止所有 RabbitMQ 消费者。
     */
    public void stopRabbitAll() {
        Object registry = getRabbitRegistry();
        for (Object container : safeGetRabbitContainers(registry)) {
            safeInvoke(container, "stop");
        }
    }

    /**
     * 启动所有 RabbitMQ 消费者。
     */
    public void startRabbitAll() {
        Object registry = getRabbitRegistry();
        for (Object container : safeGetRabbitContainers(registry)) {
            safeInvoke(container, "start");
        }
    }

    /**
     * 根据队列名称停止对应的 RabbitMQ 消费者。
     *
     * @param queueName 队列名称
     */
    public void stopRabbitByQueue(String queueName) {
        if (queueName == null) return;
        Object registry = getRabbitRegistry();
        for (Object container : safeGetRabbitContainers(registry)) {
            try {
                Method getQueues = container.getClass().getMethod("getQueueNames");
                String[] queues = (String[]) getQueues.invoke(container);
                if (Arrays.asList(queues).contains(queueName)) {
                    safeInvoke(container, "stop");
                }
            } catch (Exception ignored) {
                // 静默忽略反射异常
            }
        }
    }

    /**
     * 根据队列名称启动对应的 RabbitMQ 消费者。
     *
     * @param queueName 队列名称
     */
    public void startRabbitByQueue(String queueName) {
        if (queueName == null) return;
        Object registry = getRabbitRegistry();
        for (Object container : safeGetRabbitContainers(registry)) {
            try {
                Method getQueues = container.getClass().getMethod("getQueueNames");
                String[] queues = (String[]) getQueues.invoke(container);
                if (Arrays.asList(queues).contains(queueName)) {
                    safeInvoke(container, "start");
                }
            } catch (Exception ignored) {
                // 静默忽略反射异常
            }
        }
    }

    // ===================================================================
    //                        RocketMQ 控制逻辑
    // ===================================================================

    /**
     * 根据 Topic 停止对应的 RocketMQ 消费者。
     * <p>
     * 注意：若 {@code topic} 为 {@code null}，当前实现不会执行任何操作（因无法遍历所有 Topic）。
     *
     * @param topic Topic 名称
     */
    public void stopRocketByTopic(String topic) {
        if (!isRocketMQ() || topic == null) {
            return;
        }
        context.getBeansOfType(Object.class).values().forEach(bean -> {
            try {
                Method getTopic = bean.getClass().getMethod("getTopic");
                Object t = getTopic.invoke(bean);
                if (topic.equals(String.valueOf(t))) {
                    safeInvoke(bean, "shutdown");
                }
            } catch (Exception ignored) {
                // 静默忽略反射异常
            }
        });
    }

    /**
     * 根据 Topic 启动对应的 RocketMQ 消费者。
     * <p>
     * 注意：若 {@code topic} 为 {@code null}，当前实现不会执行任何操作。
     *
     * @param topic Topic 名称
     */
    public void startRocketByTopic(String topic) {
        if (!isRocketMQ() || topic == null) {
            return;
        }
        context.getBeansOfType(Object.class).values().forEach(bean -> {
            try {
                Method getTopic = bean.getClass().getMethod("getTopic");
                Object t = getTopic.invoke(bean);
                if (topic.equals(String.valueOf(t))) {
                    safeInvoke(bean, "start");
                }
            } catch (Exception ignored) {
                // 静默忽略反射异常
            }
        });
    }

    // ===================================================================
    //                        Kafka 控制逻辑
    // ===================================================================

    /**
     * 根据 Topic 暂停对应的 Kafka 消费者。
     * <p>
     * 注意：若 {@code topic} 为 {@code null}，当前实现不会执行任何操作。
     *
     * @param topic Topic 名称
     */
    public void stopKafkaByTopic(String topic) {
        if (!isKafka() || topic == null) {
            return;
        }
        context.getBeansOfType(Object.class).values().forEach(bean -> {
            try {
                Method getTopic = bean.getClass().getMethod("getTopic"); // 或 getSubscribedTopics
                Object t = getTopic.invoke(bean);
                if (topic.equals(String.valueOf(t))) {
                    safeInvoke(bean, "pause");
                }
            } catch (Exception ignored) {
                // 静默忽略反射异常
            }
        });
    }

    /**
     * 根据 Topic 恢复对应的 Kafka 消费者。
     * <p>
     * 注意：若 {@code topic} 为 {@code null}，当前实现不会执行任何操作。
     *
     * @param topic Topic 名称
     */
    public void startKafkaByTopic(String topic) {
        if (!isKafka() || topic == null) {
            return;
        }
        context.getBeansOfType(Object.class).values().forEach(bean -> {
            try {
                Method getTopic = bean.getClass().getMethod("getTopic");
                Object t = getTopic.invoke(bean);
                if (topic.equals(String.valueOf(t))) {
                    safeInvoke(bean, "resume");
                }
            } catch (Exception ignored) {
                // 静默忽略反射异常
            }
        });
    }

    // ===================================================================
    //                        统一控制接口
    // ===================================================================

    /**
     * 停止所有已识别的 MQ 消费者（RabbitMQ、RocketMQ、Kafka）。
     * <p>
     * 注意：RocketMQ 和 Kafka 的“全局停止”当前未实现（因缺乏统一注册中心），
     * 仅对 RabbitMQ 生效。如需完整支持，需扩展其实现。
     */
    public void stopAll() {
        stopRabbitAll();
        // RocketMQ/Kafka 无全局停止逻辑（topic 为 null 时方法直接返回）
        stopRocketByTopic(null);
        stopKafkaByTopic(null);
    }

    /**
     * 启动所有已识别的 MQ 消费者（RabbitMQ、RocketMQ、Kafka）。
     * <p>
     * 注意：RocketMQ 和 Kafka 的“全局启动”当前未实现，
     * 仅对 RabbitMQ 生效。
     */
    public void startAll() {
        startRabbitAll();
        startRocketByTopic(null);
        startKafkaByTopic(null);
    }

    /**
     * 根据名称（队列或 Topic）停止对应 MQ 的消费者。
     * <p>
     * 自动识别 MQ 类型并调用相应停止方法。
     *
     * @param name 队列名（RabbitMQ）或 Topic 名（RocketMQ/Kafka）
     */
    public void stop(String name) {
        if (name == null) {
            return;
        }
        if (isRabbitMQ()) {
            stopRabbitByQueue(name);
        }
        if (isRocketMQ()) {
            stopRocketByTopic(name);
        }
        if (isKafka()) {
            stopKafkaByTopic(name);
        }
    }

    /**
     * 根据名称（队列或 Topic）启动对应 MQ 的消费者。
     * <p>
     * 自动识别 MQ 类型并调用相应启动方法。
     *
     * @param name 队列名（RabbitMQ）或 Topic 名（RocketMQ/Kafka）
     */
    public void start(String name) {
        if (name == null) {
            return;
        }
        if (isRabbitMQ()) {
            startRabbitByQueue(name);
        }
        if (isRocketMQ()) {
            startRocketByTopic(name);
        }
        if (isKafka()) {
            startKafkaByTopic(name);
        }
    }
}