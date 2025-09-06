package githubcew.arguslog.core.cmd;

import picocli.CommandLine;

import java.io.PrintWriter;
import java.util.concurrent.Callable;

/**
 * 基础命令
 *
 * @author chenenwei
 */
public abstract class BaseCommand implements Callable<Integer> {

    protected static final String OK = "ok";

    protected static final Integer OK_CODE = 0;

    protected static final Integer ERROR_CODE = -1;

    protected static final String ERROR_PATH_NOT_FOUND = "Path not found!";

    protected static final String ERROR_PATH_EMPTY = "Path cannot be empty!";

    protected static final String ERROR_PARAM_ERROR = "Param error!";

    protected static final String ERROR_COMMAND_NOT_FOUND = "Command not found!";

    @CommandLine.Spec
    protected CommandLine.Model.CommandSpec spec;

    protected PicocliOutput picocliOutput;

    // 添加设置输出流的方法
    public void setOutput(PrintWriter out, PrintWriter err) {
        this.picocliOutput = new PicocliOutput(out, err);
    }

    /**
     * 执行逻辑
     * @return 结果
     */
    @Override
    public Integer call() {
        if (this.picocliOutput == null) {
            this.picocliOutput = new PicocliOutput(spec.commandLine().getOut(), spec.commandLine().getErr());
        }
        Integer result = 0;
        try {
            result= execute();
        } catch (Exception e) {
            picocliOutput.error(e.getMessage());
            return ERROR_CODE;
        }
        return result;
    }

    /**
     * 执行类有子类 实现
     * @return 0 成功 -1为失败
     * @throws Exception 异常
     */
      protected  Integer execute() throws Exception {
         return OK_CODE;
     }
}
