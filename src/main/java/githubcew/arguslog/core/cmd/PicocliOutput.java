package githubcew.arguslog.core.cmd;

import java.io.PrintWriter;

/**
 * @author Envi
 */
public class PicocliOutput {

    private final PrintWriter out;
    private final PrintWriter err;

    public boolean hasNormalOutput;

    public boolean hasErrorOutput;

    /**
     * 获取输出
     * @return PrintWriter
     */
    public PrintWriter getOut() {
        return out;
    }

    /**
     * 获取错误输出
     * @return PrintWriter
     */
    public PrintWriter getErr() {
        return err;
    }

    /**
     * 构造方法
     * @param out 正常输出
     * @param err 错误输出
     */
    public PicocliOutput(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
    }

    /**
     * 正常输出
     * @param msg 消息
     */
    public void out(String msg) {
        this.hasNormalOutput = true;
        out.println(msg);
        out.flush();
    }

    /**
     * 错误输出
     * @param msg 消息
     */
    public void error(String msg) {
        this.hasErrorOutput = true;
        err.println(msg);
        err.flush();
    }
}
