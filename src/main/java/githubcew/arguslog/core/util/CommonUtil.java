package githubcew.arguslog.core.util;

import githubcew.arguslog.core.auth.Token;
import githubcew.arguslog.core.cmd.ExecuteResult;

import java.util.Objects;

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
}


