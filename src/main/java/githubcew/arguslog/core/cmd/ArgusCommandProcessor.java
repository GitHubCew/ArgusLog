package githubcew.arguslog.core.cmd;

import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 命令处理器
 *
 * @author chenenwei
 */
public class ArgusCommandProcessor {

    /**
     * 命令执行
     * @param command 命令
     * @param args 参数
     * @return 结果
     */
    public static ExecuteResult execute(Object command, String... args) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter captured = new PrintWriter(baos, true);

        CommandLine cmd = new CommandLine(command);
        cmd.setOut(captured);
        cmd.setErr(captured);

        try {
            int exitCode = cmd.execute(args);
            String output = baos.toString(StandardCharsets.UTF_8.name());
            return exitCode == 0 ? ExecuteResult.success(output) : ExecuteResult.failed(output);
        } catch (Exception e) {
            // 异常也获取输出
            captured.flush();
            String output = baos.toString().trim();
            return output.isEmpty() ? ExecuteResult.failed(e.getMessage()) : ExecuteResult.failed(output);
        }
    }
}