package githubcew.arguslog.core.cmd.spring;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.common.util.PatternUtil;
import githubcew.arguslog.common.util.SpringUtil;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import org.springframework.context.ApplicationContext;
import picocli.CommandLine;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * spring相关命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "ioc",
        description = "spring ioc命令",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class IocCmd extends BaseCommand {

    @CommandLine.Parameters(

            index = "0",
            description = "spring ioc容器查询类型, ls: 模糊查询(支持*匹配 bean名称 和 类名称) get: 精确查询(支持按 bean名称 和 bean类型)",
            arity = "1",
            paramLabel = "operatorType"
    )
    private String operator;


    @CommandLine.Parameters(

            index = "1",
            description = "对象bean名称或者全限定类",
            arity = "0..1",
            paramLabel = "name"
    )
    private String bean;

    @Override
    protected Integer execute() throws Exception {

        try {
            if (operator.equals("get")) {
                if (bean == null) {
                    throw new Exception("至少需要指定一个名称");
                }
                picocliOutput.out(getBeans());
            }
            else if (operator.equals("ls")) {
                picocliOutput.out(listBeans());
            }
        } catch (Exception e) {
            picocliOutput.error(e.getMessage());
            return ERROR_CODE;
        }

        return OK_CODE;
    }

    /**
     * 获取beans
     * @return 类名
     * @throws ClassNotFoundException 找不到类
     */
    public String getBeans () throws ClassNotFoundException {

        ApplicationContext applicationContext = ContextUtil.context();
        // 查询某种类型的类列表
        if (bean.contains(".") || bean.contains("/")) {
            Class<?> type = null;
            try {
                type = Class.forName(bean.replace("/", "."));
            }
            catch (ClassNotFoundException e) {
                throw new ClassNotFoundException("找不到类：" + bean);
            }
            List<? extends SpringUtil.BeanInfo<?>> beansOfType = SpringUtil.getBeansOfTypeWithInfo(type);
            // 按照order排序
            beansOfType.sort(Comparator.comparing(SpringUtil.BeanInfo::getOrder));

            Set<String> classes = new LinkedHashSet<>();
            beansOfType.forEach(beanInfo -> {
                classes.add(format(beanInfo.getBeanName(), beanInfo.getBeanClass(), beanInfo.getOrder()));
            });
            if (classes.size() > 0) {
                return String.join("\n", classes)
                        + "\n"
                        + " (" + classes.size() + ")";
            }
        }
        else {
            try {
                Object object = applicationContext.getBean(this.bean);
                return format(this.bean, object.getClass(), SpringUtil.getOrder(object));
            } catch (Exception e) {
                //
            }
        }
        return "";
    }

    /**
     * 获取beans
     * @return 类
     */
    public String listBeans () {

        ApplicationContext applicationContext = ContextUtil.context();
        String[] beanNames = applicationContext.getBeanDefinitionNames();
        Set<String> classes = new HashSet<>();
        for (String beanName : beanNames) {
            Class<?> beanType = applicationContext.getType(beanName);
            if (Objects.isNull(bean)) {
                if (!Objects.isNull(beanType)) {
                    classes.add(format(beanName, beanType, null));
                }
            }
            else {
                if (PatternUtil.match(beanName, bean) || !Objects.isNull(beanType) && PatternUtil.match(beanType.getName(), bean)) {
                    if (!Objects.isNull(beanType)) {
                        classes.add(format(beanName, beanType, null));
                    }
                }
            }
        }
        if (classes.isEmpty()) {
            return "";
        }
        return classes.stream().sorted().collect(Collectors.joining("\n"))
                + "\n"
                + " (" + classes.size() + ")";
    }

    /**
     * 格式化输出
     * @param beanName 名称
     * @param beanType 类型
     * @param order     顺序
     * @return 格式化输出
     */
    private String format (String beanName, Class<?> beanType, Integer order) {

        String formatter = beanName + " => " + OutputWrapper.wrapperCopy(beanType.getName());
        if (order != null) {
            formatter += " (order: " + order + ")";
        }
        return formatter;
    }
}
