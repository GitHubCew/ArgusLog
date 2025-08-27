package githubcew.arguslog.core.cmd;

import lombok.Data;

/**
 * 命令执行结果
 * @author chenenwei
 */
@Data
public class ExecuteResult {

    public static final String OK = "ok";

    public static final Integer SUCCESS = 1;

    public static final Integer FAILED = 0;

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


    public static ExecuteResult success(String data) {
        return new ExecuteResult(SUCCESS, data);
    }

    public static ExecuteResult failed(String data) {
        return new ExecuteResult(FAILED, data);
    }
}
