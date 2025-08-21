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

    public RequestCommand() {}

    public RequestCommand(String command, String[] args) {
        this.command = command;
        this.args = args;
    }
}
