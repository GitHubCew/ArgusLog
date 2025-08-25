package githubcew.arguslog.web;

import githubcew.arguslog.web.auth.Token;
import githubcew.arguslog.core.cmd.ExecuteResult;
import lombok.Data;

/**
 * Argus response
 * @author chenenwei
 */
@Data
public class ArgusResponse {

    /**
     * token
     */
    private Token token;

    /**
     * 命令执行结果
     */
    private ExecuteResult executeResult;
}
