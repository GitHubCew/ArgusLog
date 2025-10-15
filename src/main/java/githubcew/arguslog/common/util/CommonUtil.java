package githubcew.arguslog.common.util;

import org.objectweb.asm.Type;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * 公共工具
 *
 * @author chenenwei
 */
public class CommonUtil {

    /**
     * 生成指定格式的方法签名
     * 格式：全限定类名.方法名(参数类型1,参数类型2) 返回类型
     *
     * @param method 方法
     * @return 方法签名
     */
    public static String generateSignature(Method method) {
        return method.getDeclaringClass().getName() +
                "." +
                method.getName() +
                getParameterTypes(method) +
                " " +
                method.getReturnType().getSimpleName();
    }

    public static String generateAsmMethodDesc(Method method) {
        return Type.getMethodDescriptor(
                Type.getType(method.getReturnType()),
                Arrays.stream(method.getParameterTypes())
                        .map(Type::getType)
                        .toArray(Type[]::new)
        );
    }

    /**
     * 获取参数类型
     *
     * @param method 方法
     * @return 参数类型
     */
    private static String getParameterTypes(Method method) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length == 0) {
            return "()";
        }

        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < params.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(params[i].getSimpleName());
        }
        sb.append(")");
        return sb.toString();
    }


    /**
     * 将类名中的 '.' 替换为 '/'。
     *
     * @param className 类名（点号格式）
     * @return 斜杠格式的类名
     */
    public static String toSlash(String className) {
        return className.replace('.', '/');
    }

    /**
     * 将类名中的 '/' 替换为 '.'。
     *
     * @param className 类名（斜杠格式）
     * @return 点号格式的类名
     */
    public static String toDot(String className) {
        return className.replace('/', '.');
    }

    /**
     * 提取异常信息
     * @param e 异常
     * @return 异常信息
     */
    public static String extractException(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}


