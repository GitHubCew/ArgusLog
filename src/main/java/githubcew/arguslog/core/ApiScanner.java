package githubcew.arguslog.core;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

/**
 * 接口扫描器
 * @author  chenenwei
 */
@Component
public class ApiScanner implements ApplicationListener<ContextRefreshedEvent> {

    @Autowired
    private RequestMappingHandlerMapping requestMappingHandlerMapping;

    /**
     * Spring容器初始化完成后，扫描接口
     * @param event Spring事件
     */
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        scan();
    }

    /**
     * 扫描接口
     */
    private void scan () {
        // 获取Spring MVC中所有的RequestMapping信息
        Map<RequestMappingInfo, HandlerMethod> handlerMethods = requestMappingHandlerMapping.getHandlerMethods();

        // 遍历所有映射关系
        handlerMethods.forEach((info, handlerMethod) -> {
            Set<String> urlPatterns = info.getPatternsCondition().getPatterns();
            Method method = handlerMethod.getMethod();
            urlPatterns.forEach(url ->{
                if (!url.startsWith("/")) {
                    url = "/" + url;
                }
                Cache.methodCache.put(url, method);
            });
        });
    }
}
