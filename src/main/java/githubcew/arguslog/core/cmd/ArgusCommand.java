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
     * 命令介绍
     */
    private String introduction;

    /**
     * 命令例子(中文)
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

    public ArgusCommand(String cmd, String introduction, String usage, String example, Integer order) {
        this.cmd = cmd;
        this.usage = usage;
        this.introduction = introduction;
        this.example = example;
    }
}
