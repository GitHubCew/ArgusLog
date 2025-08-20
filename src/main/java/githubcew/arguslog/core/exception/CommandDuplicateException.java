package githubcew.arguslog.core.exception;

import lombok.Data;

/**
 * 命令重复异常
 * @author chenenwei
 */
@Data
public class CommandDuplicateException extends RuntimeException{

    /**
     * 构造方法
     * @param command
     */
    public CommandDuplicateException(String command) {
        throw new RuntimeException("Command [" + command + "] is already registered.");
    }


}
