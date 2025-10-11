//package githubcew.arguslog.processor;
//
//import org.springframework.beans.factory.config.BeanPostProcessor;
//import org.springframework.stereotype.Component;
//
//import java.lang.reflect.Method;
//import java.lang.reflect.Proxy;
//
///**
// * 消息队列处理器
// *
// * @author chenenwei
// */
//@Component
//public class MqBeanPostProcessor implements BeanPostProcessor {
//
//    @Override
//    public Object postProcessAfterInitialization(Object bean, String beanName) {
//        // 1. RabbitMQ
//        String className = bean.getClass().getName();
//        if (className.equals("org.springframework.amqp.rabbit.core.RabbitTemplate")) {
//            return createJdkProxy(bean, org.springframework.amqp.core.AmqpTemplate.class);
//        }
//
//        // 2. Kafka
//        if (className.equals("org.springframework.kafka.core.KafkaTemplate")) {
//            return createJdkProxy(bean, org.springframework.kafka.core.KafkaOperations.class);
//        }
//
//        // 3. RocketMQ (Spring 封装)
//        if (className.equals("org.springframework.rocketmq.core.RocketMQTemplate")) {
//            Class<?> aClass = Class.forName("org.springframework.rocketmq.core.RocketMQTemplate");
//            return createJdkProxy(bean, aClass);
//        }
//
//        // 可继续扩展其他 MQ 客户端...
//
//        return bean;
//    }
//
//    // ===== JDK 动态代理（适用于有接口的类）=====
//    private Object createJdkProxy(Object target, Class<?> interfaceType) {
//        return Proxy.newProxyInstance(
//                interfaceType.getClassLoader(),
//                new Class[]{interfaceType},
//                (proxy, method, args) -> {
//                    beforeInvoke(target, method, args);
//                    try {
//                        Object result = method.invoke(target, args);
//                        afterSuccess(target, method, args, result);
//                        return result;
//                    } catch (Exception e) {
//                        afterException(target, method, args, e);
//                        throw e;
//                    }
//                }
//        );
//    }
//
//    // ===== CGLIB 代理（适用于无接口的类）=====
//    private Object createCglibProxy(Object target) {
//        org.springframework.cglib.proxy.Enhancer enhancer = new org.springframework.cglib.proxy.Enhancer();
//        enhancer.setSuperclass(target.getClass());
//        enhancer.setCallback((org.springframework.cglib.proxy.MethodInterceptor)
//                (obj, method, args, proxy) -> {
//                    beforeInvoke(target, method, args);
//                    try {
//                        Object result = method.invoke(target, args);
//                        afterSuccess(target, method, args, result);
//                        return result;
//                    } catch (Exception e) {
//                        afterException(target, method, args, e);
//                        throw e;
//                    }
//                });
//        return enhancer.create();
//    }
//
//    // ===== 横切逻辑（可替换为日志、Metrics、Tracing 等）=====
//    private void beforeInvoke(Object target, Method method, Object[] args) {
//        String mqType = getMqType(target);
//        System.out.printf("[MQ-PROXY] %s 调用方法: %s%n", mqType, method.getName());
//    }
//
//    private void afterSuccess(Object target, Method method, Object[] args, Object result) {
//        // 例如：上报成功指标
//    }
//
//    private void afterException(Object target, Method method, Object[] args, Exception e) {
//        String mqType = getMqType(target);
//        System.err.printf("[MQ-PROXY] %s 方法 %s 抛出异常: %s%n", mqType, method.getName(), e.getMessage());
//    }
//
//    private String getMqType(Object target) {
//        if (target instanceof RabbitTemplate) return "RabbitMQ";
//        if (target instanceof KafkaTemplate) return "Kafka";
//        if (target instanceof RocketMQTemplate || target instanceof DefaultMQProducer) return "RocketMQ";
//        if (target instanceof PulsarTemplate) return "Pulsar";
//        return "Unknown MQ";
//    }
//}
