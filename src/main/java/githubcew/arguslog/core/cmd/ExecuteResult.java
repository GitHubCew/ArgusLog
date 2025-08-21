package githubcew.arguslog.core.cmd;

import lombok.Data;

/**
 * 命令执行结果
 * @author chenenwei
 */
@Data
public class ExecuteResult {

    /**
     * 状态: 1-成功, 0-失败
     */
    private Integer status;

    /**
     * 结果（中文）
     */
    private String data;


    public ExecuteResult() {
    }

    public ExecuteResult(Integer status, String data) {
        this.status = status;
        this.data = data;
    }
}
