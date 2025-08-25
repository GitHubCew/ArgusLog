package githubcew.arguslog.core.cmd;

import lombok.Data;

/**
 * 命令
 * @author chennewei
 */
@Data
public class ArgusCommand{

    /**
     * 命令
     */
    private String cmd;

    /**
     * 命令用法
     */
    private String usage;

    /**
     * 命令描述
     */
    private String introduction;

    /**
     * 命令使用例子
     */
    private String example;

    public ArgusCommand() {
    }

    public ArgusCommand(String cmd, String introduction, String usage, String example) {
        this.cmd = cmd;
        this.usage = usage;
        this.introduction = introduction;
        this.example = example;
    }
}
