package githubcew.arguslog.core.cmd;

import lombok.Data;

/**
 * 请求命令
 * @author chenenwei
 */
@Data
public class RequestCommand {

    /**
     * 命令
     */
    private String command;
    /**
     * 参数
     */
    private String[] args;

    /**
     * 构造参数
     */
    public RequestCommand() {}

    /**
     * 构造参数
     * @param command 命令
     * @param args 参数
     */
    public RequestCommand(String command, String[] args) {
        this.command = command;
        this.args = args;
    }
}
