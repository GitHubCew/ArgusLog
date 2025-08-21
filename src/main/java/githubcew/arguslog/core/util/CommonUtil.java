package githubcew.arguslog.core.util;

import githubcew.arguslog.core.auth.Token;
import githubcew.arguslog.core.cmd.ExecuteResult;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.Type;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * 公共工具
 * @author chenenwei
 */
public class CommonUtil {

    /**
     * 格式化输出
     * @param token token
     * @param executeResult 执行结果
     * @return 输出
     */
    public static String formatOutput (Token token, ExecuteResult executeResult) {
        String out = "code=" + executeResult.getStatus() + "##data=" + executeResult.getData();
        if (!Objects.isNull(token) && !Objects.isNull(token.getToken())) {
            out =  "token=" + token.getToken() + "##" + out;
        }
        return out;
    }

    /**
     * 生成指定格式的方法签名
     * 格式：全限定类名.方法名(参数类型1,参数类型2) 返回类型
     */
    public static String generateSignature(Method method) {
        return method.getDeclaringClass().getName() +
                "." +
                method.getName() +
                getParameterTypes(method) +
                " " +
                method.getReturnType().getSimpleName();
    }

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
}


