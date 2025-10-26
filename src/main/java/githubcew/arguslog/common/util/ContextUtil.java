package githubcew.arguslog.common.util;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 上下文工具
 *
 * @author chenenwei
 */
@Component
public class ContextUtil implements ApplicationContextAware {

    private static ApplicationContext applicationContext;

    /**
     * 设置ApplicationContext
     *
     * @param applicationContext applicationContext
     * @throws BeansException 异常
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        ContextUtil.applicationContext = applicationContext;
    }


    public static ApplicationContext context() {
        return applicationContext;
    }

    /**
     * 获取bean
     *
     * @param beanName beanName
     * @return bean
     */
    public static Object getBean(String beanName) {
        return applicationContext.getBean(beanName);
    }

    /**
     * 获取bean
     *
     * @param beanName beanName
     * @param <T>        泛型
     * @param requiredType 类型
     * @return bean
     */
    public static <T> T getBean(String beanName, Class<T> requiredType) {
        return applicationContext.getBean(beanName, requiredType);
    }

    /**
     * 判断容器中是否存在指定类型的Bean（支持接口、抽象类、具体类）
     */
    public static boolean hasBean(Class<?> targetClass) {
        if (applicationContext == null || targetClass == null) {
            return false;
        }

        try {
            // 方法1: 直接按类型获取（精确匹配）
            if (getBeanByExactType(targetClass) != null) {
                return true;
            }

            // 方法2: 按名称获取（类名首字母小写）
            if (getBeanByName(targetClass) != null) {
                return true;
            }

            // 方法3: 查找所有实现类/子类
            if (hasBeanByAssignableType(targetClass)) {
                return true;
            }

            return false;

        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 获取指定类型的Bean（支持多种查找策略）
     */
    public static <T> T getBean(Class<T> targetClass) {
        if (applicationContext == null || targetClass == null) {
            return null;
        }

        try {
            // 策略1: 精确类型匹配
            T bean = getBeanByExactType(targetClass);
            if (bean != null) {
                return bean;
            }

            // 策略2: 按名称匹配
            bean = getBeanByName(targetClass);
            if (bean != null) {
                return bean;
            }

            // 策略3: 查找可分配的类型（实现类/子类）
            bean = getBeanByAssignableType(targetClass);
            if (bean != null) {
                return bean;
            }

            // 策略4: 考虑泛型类型
            bean = getBeanByResolvableType(targetClass);

            return bean;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 精确类型匹配
     */
    private static <T> T getBeanByExactType(Class<T> targetClass) {
        try {
            return applicationContext.getBean(targetClass);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 按Bean名称匹配（类名首字母小写）
     */
    private static <T> T getBeanByName(Class<T> targetClass) {
        try {
            String beanName = getDefaultBeanName(targetClass);
            return applicationContext.getBean(beanName, targetClass);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 查找可分配的类型（实现类/子类）
     */
    private static <T> T getBeanByAssignableType(Class<T> targetClass) {
        try {
            Map<String, T> beans = applicationContext.getBeansOfType(targetClass);
            if (beans.size() == 1) {
                return beans.values().iterator().next();
            } else if (beans.size() > 1) {
                // 多个Bean时，尝试通过主要Bean或默认名称选择
                return resolveMultipleBeans(beans, targetClass);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 检查是否存在可分配类型的Bean
     */
    private static boolean hasBeanByAssignableType(Class<?> targetClass) {
        try {
            Map<String, ?> beans = applicationContext.getBeansOfType(targetClass);
            return !beans.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 考虑泛型类型的匹配
     */
    private static <T> T getBeanByResolvableType(Class<T> targetClass) {
        try {
            String[] beanNames = applicationContext.getBeanNamesForType(
                    ResolvableType.forClass(targetClass));

            if (beanNames.length == 1) {
                return (T) applicationContext.getBean(beanNames[0]);
            } else if (beanNames.length > 1) {
                // 多个匹配时选择第一个
                return (T) applicationContext.getBean(beanNames[0]);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 处理多个同类型Bean的情况
     */
    private static <T> T resolveMultipleBeans(Map<String, T> beans, Class<T> targetClass) {
        // 策略1: 查找@Primary注解的Bean
        T primaryBean = findPrimaryBean(beans, targetClass);
        if (primaryBean != null) {
            return primaryBean;
        }

        // 策略2: 使用默认名称的Bean
        String defaultBeanName = getDefaultBeanName(targetClass);
        if (beans.containsKey(defaultBeanName)) {
            return beans.get(defaultBeanName);
        }

        // 策略3: 返回第一个Bean（带警告）
        T firstBean = beans.values().iterator().next();
        System.out.println("WARNING: Multiple beans found for type " + targetClass.getName() +
                ", using first one: " + firstBean.getClass().getName());
        return firstBean;
    }

    /**
     * 查找@Primary注解的Bean
     */
    private static <T> T findPrimaryBean(Map<String, T> beans, Class<T> targetClass) {
        for (Map.Entry<String, T> entry : beans.entrySet()) {
            Object bean = entry.getValue();
            if (applicationContext.findAnnotationOnBean(entry.getKey(),
                    org.springframework.context.annotation.Primary.class) != null) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 获取默认的Bean名称（类名首字母小写）
     */
    private static String getDefaultBeanName(Class<?> clazz) {
        String className = clazz.getSimpleName();
        return Character.toLowerCase(className.charAt(0)) + className.substring(1);
    }

    /**
     * 获取指定类型的所有Bean
     */
    public static <T> Map<String, T> getBeansOfType(Class<T> targetClass) {
        if (applicationContext == null) {
            return new HashMap<>();
        }
        try {
            return applicationContext.getBeansOfType(targetClass);
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    /**
     * 获取Bean名称列表
     */
    public static String[] getBeanNamesForType(Class<?> targetClass) {
        if (applicationContext == null) {
            return new String[0];
        }
        try {
            return applicationContext.getBeanNamesForType(targetClass);
        } catch (Exception e) {
            return new String[0];
        }
    }
}
