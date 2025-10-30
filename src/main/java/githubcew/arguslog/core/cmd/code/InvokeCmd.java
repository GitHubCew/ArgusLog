package githubcew.arguslog.core.cmd.code;

import com.fasterxml.jackson.databind.ObjectMapper;
import githubcew.arguslog.common.util.ClassUtil;
import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.cmd.BacktickConsumer;
import githubcew.arguslog.core.cmd.BaseCommand;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;
import picocli.CommandLine;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 命令行命令：动态调用指定类的方法
 */
@CommandLine.Command(
        name = "invoke",
        description = "动态调用指定类的方法",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class InvokeCmd extends BaseCommand {

    @CommandLine.Parameters(
            index = "0",
            arity = "1",
            description = "方法签名，例如: cn.demo.Test.demo(java.lang.String.class,int.class)",
            parameterConsumer = BacktickConsumer.class,
            paramLabel = "methodSignature"
    )
    private String methodSignature;

    @CommandLine.Parameters(
            index = "1",
            arity = "0..*",
            description = "方法参数: ``可用于长字符，如：json、对象参数，例如: haha `{\"a\":1}` true",
            parameterConsumer = BacktickConsumer.class,
            paramLabel = "params"
    )
    private List<String> params;

    @CommandLine.Option(
            names = {"-o", "--output"},
            description = "输出格式: object | json",
            paramLabel = "object|json",
            defaultValue = "json"
    )
    private String output;

    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    protected Integer execute() throws Exception {
        // 1. 解析方法签名
        MethodSignature signature;
        try {
            signature = parseMethodSignature(methodSignature);
        } catch (Exception e) {
            throw new IllegalArgumentException("方法签名格式错误：" + methodSignature);
        }

        // 2. 获取类与实例
        ClassInstance classInstance = getClassInstance(signature.getClassName());

        // 3. 解析参数值
        Object[] args;
        try {
            args = ClassUtil.ArgumentParser.parseArguments(signature.getParamTypes(), params);
        } catch (Exception e) {
            throw new IllegalArgumentException("参数解析或转换错误：" + e.getMessage());
        }

        // 4. 查找方法并调用
        Method method = ClassUtil.MethodFinder.findMethodWildcard(
                classInstance.getClazz(),
                signature.getMethodName(),
                signature.getParamTypes()
        );

        if (method == null) {
            throw new IllegalArgumentException("找不到方法：" + methodSignature);
        }

        Object instance = Modifier.isStatic(method.getModifiers())
                ? null
                : classInstance.getInstance();

        Object result;
        try {
            result = method.invoke(instance, args);
        } catch (Throwable e) {
            throw new RuntimeException(CommonUtil.extractException(e));
        }

        // 5. 输出结果
        outputResult(result);
        return OK_CODE;
    }

    /**
     * 解析方法签名
     *
     * <div>方法签名格式：className.methodName(paramType1, paramType2)</div>
     *
     * @param methodSignature 方法签名字符串
     * @return 解析后的MethodSignature对象
     * @throws IllegalArgumentException 当方法签名格式错误时抛出
     * @throws ClassNotFoundException   当参数类型类不存在时抛出
     */
    private MethodSignature parseMethodSignature(String methodSignature) throws ClassNotFoundException {
        int idx = methodSignature.indexOf('(');
        if (idx < 0) {
            throw new IllegalArgumentException("方法签名格式错误：" + methodSignature);
        }

        String methodFull = methodSignature.substring(0, idx).trim();
        int lastDot = methodFull.lastIndexOf('.');
        if (lastDot < 0) {
            throw new IllegalArgumentException("方法签名缺少类名或方法名：" + methodSignature);
        }

        String className = methodFull.substring(0, lastDot);
        String methodName = methodFull.substring(lastDot + 1);

        String paramTypeStr = methodSignature.substring(idx + 1, methodSignature.lastIndexOf(')')).trim();
        List<String> typeNames = new ArrayList<>();
        if (!paramTypeStr.isEmpty()) {
            for (String s : paramTypeStr.split(",")) {
                typeNames.add(s.trim().replace(".class", ""));
            }
        }

        // 构造参数类型
        Class<?>[] paramTypes = new Class[typeNames.size()];
        for (int i = 0; i < typeNames.size(); i++) {
            paramTypes[i] = ClassUtil.ClassParser.parseClass(typeNames.get(i));
        }

        return new MethodSignature(className, methodName, paramTypes);
    }

    /**
     * 获取类实例
     *
     * <div>优先从Spring应用上下文中获取Bean实例，如果不存在则创建新的类实例</div>
     *
     * @param className 类名
     * @return 包含类对象和实例的ClassInstance对象
     * @throws ClassNotFoundException 当类不存在时抛出
     */
    private ClassInstance getClassInstance(String className) throws ClassNotFoundException {
        Class<?> clazz = ClassUtil.ClassParser.parseClass(className);
        Object target = null;

        ApplicationContext ctx = ContextUtil.context();
        if (ctx != null) {
            try {
                String[] beans = ctx.getBeanNamesForType(clazz);
                if (beans.length > 0) {
                    target = ctx.getBean(beans[0]);
                    clazz = AopProxyUtils.ultimateTargetClass(target);
                }
            } catch (Throwable ignored) {
                // Spring Bean 获取失败忽略
            }
        }

        return new ClassInstance(clazz, target);
    }

    /**
     * 输出结果
     *
     * <div>根据输出格式配置，支持JSON格式或普通字符串格式输出</div>
     *
     * @param result 要输出的结果对象
     */
    private void outputResult(Object result) {
        if (result != null) {
            if (Objects.isNull(output) || "json".equalsIgnoreCase(output)) {
                try {
                    picocliOutput.out(mapper.writeValueAsString(result));
                } catch (Exception e) {
                    picocliOutput.out(result.toString());
                }
            } else {
                picocliOutput.out(result.toString());
            }
        } else {
            picocliOutput.out("null");
        }
    }

    /**
     * 方法签名内部类
     *
     * <div>用于封装解析后的方法签名信息</div>
     */
    private static class MethodSignature {
        private final String className;
        private final String methodName;
        private final Class<?>[] paramTypes;

        /**
         * 构造函数
         *
         * @param className  类名
         * @param methodName 方法名
         * @param paramTypes 参数类型数组
         */
        public MethodSignature(String className, String methodName, Class<?>[] paramTypes) {
            this.className = className;
            this.methodName = methodName;
            this.paramTypes = paramTypes;
        }

        /**
         * @return 类名
         */
        public String getClassName() {
            return className;
        }

        /**
         * @return 方法名
         */
        public String getMethodName() {
            return methodName;
        }

        /**
         * @return 参数类型数组
         */
        public Class<?>[] getParamTypes() {
            return paramTypes;
        }
    }

    /**
     * 类实例内部类
     *
     * <div>用于封装类对象和对应的实例</div>
     */
    private static class ClassInstance {
        private final Class<?> clazz;
        private final Object instance;

        /**
         * 构造函数
         *
         * @param clazz    类对象
         * @param instance 类实例
         */
        public ClassInstance(Class<?> clazz, Object instance) {
            this.clazz = clazz;
            this.instance = instance;
        }

        /**
         * @return 类对象
         */
        public Class<?> getClazz() {
            return clazz;
        }

        /**
         * @return 类实例
         */
        public Object getInstance() {
            return instance;
        }
    }
}