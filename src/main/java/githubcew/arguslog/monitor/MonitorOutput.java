package githubcew.arguslog.monitor;

import lombok.Data;

/**
 * 输出内容
 * @author  chenenwei
 */
@Data
public class MonitorOutput {

    /**
     * 接口参数
     */
    private Object param;

    /**
     * 接口耗时（毫秒）
     */
    private Long time;

    /**
     * 接口返回结果
     */
    private Object result;

    /**
     * 异常
     */
    private Exception exception;
}
