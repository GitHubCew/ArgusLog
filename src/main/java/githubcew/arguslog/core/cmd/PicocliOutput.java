package githubcew.arguslog.core.cmd;

import java.io.PrintWriter;

/**
 * @author Envi
 */
public class PicocliOutput {

    private final PrintWriter out;
    private final PrintWriter err;

    public PrintWriter getOut() {
        return out;
    }

    public PrintWriter getErr() {
        return err;
    }

    public PicocliOutput(PrintWriter out, PrintWriter err) {
        this.out = out;
        this.err = err;
    }

    public void out(String msg) {
        out.println(msg);
        out.flush();
    }

    public void error(String msg) {
        err.println(msg);
        err.flush();
    }
}
