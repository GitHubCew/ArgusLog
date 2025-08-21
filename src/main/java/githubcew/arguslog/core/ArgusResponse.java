package githubcew.arguslog.core;

import githubcew.arguslog.core.auth.Token;
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
