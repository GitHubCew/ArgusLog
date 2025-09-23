package githubcew.arguslog.common.util;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Spring 上下文工具类，提供从 ApplicationContext 中按类型、名称获取 Bean 的增强功能，
 * 支持识别通过 {@code RegistrationBean}（如 FilterRegistrationBean）注册的组件，
 * 并封装 Bean 的元信息（如来源、优先级等），适用于需要统一管理或排序组件（如过滤器、Servlet）的场景。
 *
 * @author chenenwei
 * @since 1.0.0
 */
public class SpringUtil {

    /**
     * 根据类的全限定名从 Spring 上下文中查找并返回对应的 Bean 实例。
     * <p>
     * 遍历所有 Bean 定义，比较其 {@link Class#getName()} 是否匹配指定类名。
     * 若未找到或类型为 null，则返回 {@code null}。
     *
     * @param className 类的全限定名（如 "com.example.MyService"）
     * @return 匹配的 Bean 实例，若未找到则返回 {@code null}
     */
    public static Object getByFullClassName(String className) {
        ApplicationContext context = ContextUtil.context();
        String[] beanNames = context.getBeanDefinitionNames();
        for (String beanName : beanNames) {
            Object bean = context.getBean(beanName);
            Class<?> type = context.getType(beanName);
            if (Objects.isNull(type)) {
                continue;
            }
            if (type.getName().equals(className)) {
                return bean;
            }
        }
        return null;
    }

    /**
     * 获取指定类型的所有 Bean 实例，包括：
     * <ul>
     *   <li>直接注册的 Bean（通过 {@code @Component}、{@code @Bean} 等）</li>
     *   <li>通过 {@code RegistrationBean}（如 {@link FilterRegistrationBean}）注册的组件</li>
     * </ul>
     * <p>
     * 返回的 Map 中，直接 Bean 使用原 Bean 名称，RegistrationBean 注册的组件使用前缀 {@code "registered_"} + 原名，避免键冲突。
     *
     * @param targetType 目标类型，如 {@code Filter.class}、{@code Servlet.class}
     * @param <T>        目标类型泛型
     * @return 包含所有匹配实例的 Map，键为 Bean 名称，值为实例对象
     */
    public <T> Map<String, T> getBeansOfTypeComprehensive(Class<T> targetType) {
        ApplicationContext applicationContext = ContextUtil.context();

        // 1. 首先获取直接注册的Bean
        Map<String, T> directBeans = applicationContext.getBeansOfType(targetType);
        Map<String, T> result = new LinkedHashMap<>(directBeans);

        // 2. 获取通过RegistrationBean注册的组件
        Map<String, T> registeredComponents = getComponentsFromRegistrationBeans(targetType);
        registeredComponents.forEach((name, component) -> {
            // 避免重复，使用特定命名规则
            String key = "registered_" + name;
            result.put(key, component);
        });

        return result;
    }

    /**
     * 从 Spring 上下文中的各类 {@code RegistrationBean} 中提取指定类型的组件。
     * <p>
     * 支持的 RegistrationBean 类型包括：
     * <ul>
     *   <li>{@link org.springframework.boot.web.servlet.FilterRegistrationBean}</li>
     *   <li>{@link org.springframework.boot.web.servlet.ServletRegistrationBean}</li>
     *   <li>{@link org.springframework.boot.web.servlet.ServletListenerRegistrationBean}</li>
     *   <li>{@link org.springframework.boot.web.servlet.DynamicRegistrationBean}</li>
     *   <li>WebServices 的 {@code ServletRegistrationBean}</li>
     * </ul>
     * <p>
     * 通过反射调用 {@code getFilter()}、{@code getServlet()} 等方法提取实际组件。
     *
     * @param targetType 目标组件类型
     * @param <T>        泛型类型
     * @return 组件名称到实例的映射
     */
    @SuppressWarnings("unchecked")
    private <T> Map<String, T> getComponentsFromRegistrationBeans(Class<T> targetType) {
        ApplicationContext applicationContext = ContextUtil.context();
        Map<String, T> components = new LinkedHashMap<>();

        // 获取所有RegistrationBean类型
        getRegistrationBeanClasses().forEach(registrationBeanClass -> {
            Map<String, ?> registrationBeans = applicationContext.getBeansOfType(registrationBeanClass);

            registrationBeans.forEach((beanName, registrationBean) -> {
                try {
                    Object component = extractComponentFromRegistrationBean(registrationBean);
                    if (component != null && targetType.isInstance(component)) {
                        components.put(beanName, (T) component);
                    }
                } catch (Exception e) {
                    // 忽略提取失败的bean
                }
            });
        });

        return components;
    }

    /**
     * 获取 Spring Boot 中常见的 {@code RegistrationBean} 类型集合。
     * <p>
     * 通过类名反射加载，若类不存在（如未引入相关模块），则跳过。
     *
     * @return 支持的 RegistrationBean 类型列表
     */
    private static List<Class<?>> getRegistrationBeanClasses() {
        List<Class<?>> classes = new ArrayList<>();

        // 常见的RegistrationBean类型
        String[] registrationBeanTypeNames = {
                "org.springframework.boot.web.servlet.FilterRegistrationBean",
                "org.springframework.boot.web.servlet.ServletRegistrationBean",
                "org.springframework.boot.web.servlet.ServletListenerRegistrationBean",
                "org.springframework.boot.web.servlet.DynamicRegistrationBean",
                "org.springframework.boot.webservices.servlet.ServletRegistrationBean",
                // 可以继续添加其他RegistrationBean类型
        };

        for (String className : registrationBeanTypeNames) {
            try {
                Class<?> clazz = Class.forName(className);
                classes.add(clazz);
            } catch (ClassNotFoundException e) {
                // 类型不存在，跳过
            }
        }

        return classes;
    }

    /**
     * 从 {@code RegistrationBean} 实例中提取实际注册的组件（如 Filter、Servlet、Listener）。
     * <p>
     * 尝试依次调用以下方法：
     * <ul>
     *   <li>{@code getFilter()}</li>
     *   <li>{@code getServlet()}</li>
     *   <li>{@code getListener()}</li>
     *   <li>{@code getRegistration()}</li>
     * </ul>
     * 返回第一个非空结果。
     *
     * @param registrationBean RegistrationBean 实例
     * @return 提取出的组件对象，若提取失败或无组件则返回 {@code null}
     */
    private static Object extractComponentFromRegistrationBean(Object registrationBean) {
        // 尝试通过反射调用getter方法获取组件
        String[] getterMethods = {"getFilter", "getServlet", "getListener", "getRegistration"};

        for (String methodName : getterMethods) {
            try {
                Method method = registrationBean.getClass().getMethod(methodName);
                Object component = method.invoke(registrationBean);
                if (component != null) {
                    return component;
                }
            } catch (NoSuchMethodException e) {
                // 方法不存在，继续尝试下一个
            } catch (Exception e) {
                // 调用失败，继续尝试
            }
        }

        return null;
    }

    /**
     * 获取指定类型的所有 Bean 实例，并封装为 {@link BeanInfo} 对象，包含来源、优先级等元信息。
     * <p>
     * 处理顺序：
     * <ol>
     *   <li>直接注册的 Bean</li>
     *   <li>通过 RegistrationBean 注册的组件（按具体类优先于抽象类的顺序处理）</li>
     * </ol>
     * <p>
     * 每个 Bean 仅处理一次，避免重复。
     *
     * @param targetType 目标类型（如 {@code Filter.class}）
     * @param <T>        泛型类型
     * @return 包含元信息的 BeanInfo 列表
     */
    public static <T> List<BeanInfo<T>> getBeansOfTypeWithInfo(Class<T> targetType) {
        List<BeanInfo<T>> beanInfos = new ArrayList<>();
        Set<String> processedBeanNames = new HashSet<>();

        ApplicationContext applicationContext = ContextUtil.context();

        // 直接注册的Bean
        Map<String, T> directBeans = applicationContext.getBeansOfType(targetType);
        directBeans.forEach((name, bean) -> {
            Integer order = getOrder(bean);
            beanInfos.add(new BeanInfo<>(name, bean, bean.getClass(), "DirectBean", order));
            processedBeanNames.add(name);
        });

        // 先处理具体的子类，再处理抽象的父类
        List<Class<?>> registrationBeanClasses = getRegistrationBeanClassesByPriority();

        for (Class<?> registrationBeanClass : registrationBeanClasses) {
            Map<String, ?> registrationBeans = applicationContext.getBeansOfType(registrationBeanClass);

            registrationBeans.forEach((regBeanName, registrationBean) -> {
                // 如果已经处理过这个Bean，跳过
                if (processedBeanNames.contains(regBeanName)) {
                    return;
                }

                try {
                    Object component = extractComponentFromRegistrationBean(registrationBean);
                    if (targetType.isInstance(component)) {
                        String source = registrationBeanClass.getSimpleName() + ":" + regBeanName;
                        beanInfos.add(new BeanInfo<>(regBeanName, (T) component, component.getClass(), source, null));
                        processedBeanNames.add(regBeanName); // 标记为已处理
                    }
                } catch (Exception e) {
                    // 忽略异常
                }
            });
        }

        return beanInfos;
    }

    /**
     * 按优先级排序 RegistrationBean 类型：具体实现类优先于抽象基类。
     * <p>
     * 用于在 {@link #getBeansOfTypeWithInfo(Class)} 中避免重复提取（如 FilterRegistrationBean 和其父类 DynamicRegistrationBean）。
     *
     * @return 按优先级排序的 RegistrationBean 类型列表
     */
    private static List<Class<?>> getRegistrationBeanClassesByPriority() {
        List<Class<?>> classes = new ArrayList<>();

        // 具体的实现类（优先级高）
        String[] concreteClasses = {
                "org.springframework.boot.web.servlet.FilterRegistrationBean",
                "org.springframework.boot.web.servlet.ServletRegistrationBean",
                "org.springframework.boot.web.servlet.ServletListenerRegistrationBean",
                "org.springframework.boot.webservices.servlet.ServletRegistrationBean"
        };

        // 抽象的父类（优先级低）
        String[] abstractClasses = {
                "org.springframework.boot.web.servlet.DynamicRegistrationBean",
                "org.springframework.boot.web.servlet.RegistrationBean"
        };

        // 先添加具体类
        for (String className : concreteClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                classes.add(clazz);
            } catch (ClassNotFoundException e) {
                // 忽略不存在的类
            }
        }

        // 再添加抽象类
        for (String className : abstractClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                classes.add(clazz);
            } catch (ClassNotFoundException e) {
                // 忽略不存在的类
            }
        }

        return classes;
    }

    /**
     * Bean 信息封装类，包含 Bean 名称、实例、类型、来源和优先级。
     * <p>
     * 用于统一管理从不同来源（直接注册、RegistrationBean）获取的组件，并支持按 {@link Ordered} 排序。
     *
     * @param <T> Bean 实例类型
     */
    public static class BeanInfo<T> {
        private final String beanName;
        private final T beanInstance;
        private final String source;
        private final int order;
        private Class<?> beanClass;

        /**
         * 构造方法。
         *
         * @param beanName     Bean 名称
         * @param beanInstance Bean 实例
         * @param beanClass    Bean 实际类型
         * @param source       来源描述（如 "DirectBean" 或 "FilterRegistrationBean:myFilter"）
         * @param order        优先级（若为 {@code null}，则尝试从 RegistrationBean 中提取或使用默认值）
         */
        public BeanInfo(String beanName, T beanInstance, Class<?> beanClass, String source, Integer order) {
            this.beanName = beanName;
            this.beanInstance = beanInstance;
            this.beanClass = beanClass;
            this.source = source;
            if (null != order) {
                this.order = order;
            } else {
                this.order = extractOrder(beanName);
            }
        }

        /**
         * 从 RegistrationBean 名称中提取其定义的优先级（Order）。
         * <p>
         * 尝试从 Spring 上下文中获取对应 Bean，并检查是否实现 {@link Ordered} 接口。
         *
         * @param beanName RegistrationBean 的名称
         * @return 优先级值，若无法获取则返回 {@link Ordered#LOWEST_PRECEDENCE}
         */
        private int extractOrder(String beanName) {
            try {
                ApplicationContext context = ContextUtil.context();
                Object registrationBean = context.getBean(beanName);

                // 检查是否是Ordered类型（包括其子类）
                if (registrationBean instanceof Ordered) {
                    return ((Ordered) registrationBean).getOrder();
                }
            } catch (Exception e) {
                // 忽略异常，如Bean不存在或类型错误
            }
            return Ordered.LOWEST_PRECEDENCE;
        }

        // Getter 方法

        /**
         * 获取 Bean 名称。
         *
         * @return Bean 名称
         */
        public String getBeanName() {
            return beanName;
        }

        /**
         * 获取 Bean 实例。
         *
         * @return Bean 实例
         */
        public T getBeanInstance() {
            return beanInstance;
        }

        /**
         * 获取 Bean 来源描述。
         *
         * @return 来源字符串（如 "DirectBean" 或 "FilterRegistrationBean:myFilter"）
         */
        public String getSource() {
            return source;
        }

        /**
         * 获取 Bean 优先级。
         *
         * @return 优先级数值，值越小优先级越高
         */
        public int getOrder() {
            return order;
        }

        /**
         * 获取 Bean 实际类型。
         *
         * @return Bean 的 Class 对象
         */
        public Class<?> getBeanClass() {
            return beanClass;
        }
    }

    /**
     * 获取指定 Bean 的优先级（Order）。
     * <p>
     * 判断顺序：
     * <ol>
     *   <li>检查类上是否有 {@link Order} 注解</li>
     *   <li>检查是否实现 {@link Ordered} 接口</li>
     *   <li>默认返回 {@link Ordered#LOWEST_PRECEDENCE}</li>
     * </ol>
     *
     * @param bean Bean 实例
     * @return 优先级值
     */
    public static Integer getOrder(Object bean) {
        // 优先检查@Order注解
        Order orderAnnotation = AnnotationUtils.findAnnotation(bean.getClass(), Order.class);
        if (orderAnnotation != null) {
            return orderAnnotation.value();
        }

        // 检查Ordered接口
        if (bean instanceof Ordered) {
            return ((Ordered) bean).getOrder();
        }

        // 默认返回最低优先级
        return Ordered.LOWEST_PRECEDENCE;
    }
}