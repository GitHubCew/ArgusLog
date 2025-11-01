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

    public static ExecuteResult success (String data, Long costTime) {
        ExecuteResult executeResult = new ExecuteResult(SUCCESS, data);
        if (costTime != null) {
            executeResult.setData(executeResult.getData() + "\n耗时：" + formatTime(costTime));
        }
        return executeResult;
    }

    public static ExecuteResult failed(String data) {
        return new ExecuteResult(FAILED, data);
    }

    public static ExecuteResult failed (String data, Long costTime) {
        ExecuteResult executeResult = new ExecuteResult(FAILED, data);
        if (costTime != null) {
            executeResult.setData(executeResult.getData() + "\n耗时：" + formatTime(costTime));
        }
        return executeResult;
    }


    /**
     * 格式化耗时
     * @param costTime  耗时
     * @return 结果
     */
    public static String formatTime(long costTime) {
        if (costTime < 1000) {
            return costTime + "ms";
        }
        return costTime / 1000 + "s";
    }
}
