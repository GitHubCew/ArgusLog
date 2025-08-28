package githubcew.arguslog.common.exception;

import lombok.Data;

/**
 * 命令重复异常
 *
 * @author chenenwei
 */
@Data
public class CommandDuplicateException extends RuntimeException {

    /**
     * 构造方法
     *
     * @param command 命令
     */
    public CommandDuplicateException(String command) {
        throw new RuntimeException("Command [" + command + "] is already registered.");
    }


}
