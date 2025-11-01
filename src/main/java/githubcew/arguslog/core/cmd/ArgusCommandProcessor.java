package githubcew.arguslog.core.cmd;

import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.web.ArgusRequestContext;
import githubcew.arguslog.web.ArgusUserContext;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.*;

/**
 * 命令处理器
 * <p>
 * 用于统一执行 {@link picocli.CommandLine} 命令对象，
 * 支持输出捕获与超时控制。
 *
 * <p>示例：
 * <pre>
 * ExecuteResult result = ArgusCommandProcessor.execute(new FindCmd(), 5, "-p", "com.demo");
 * </pre>
 *
 * @author
 *  chenenwei
 */
public class ArgusCommandProcessor {

    /** 默认命令超时时间（秒） */
    private static final int DEFAULT_TIMEOUT_SECONDS = 10;

    /**
     * 执行命令（默认超时 10 秒）
     *
     * @param command 命令对象
     * @param args    命令参数
     * @return 执行结果
     */
    public static ExecuteResult execute(Object command, String... args) {
        ArgusProperties argusProperties = null;
        try {
            argusProperties = ContextUtil.getBean(ArgusProperties.class);
        }
        catch (Exception e) {
            // 忽略
        }

        int timeout = DEFAULT_TIMEOUT_SECONDS;

        if (!Objects.isNull(argusProperties)) {
            timeout = argusProperties.getCmdExecuteTimeout();
        }
        return execute(command, timeout, args);
    }

    /**
     * 执行命令（带超时）
     *
     * @param command   命令对象
     * @param timeoutSec 超时时间（秒）
     * @param args      命令参数
     * @return 执行结果
     */
    public static ExecuteResult execute(Object command, int timeoutSec, String... args) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintWriter captured = new PrintWriter(baos, true);

        CommandLine cmd = new CommandLine(command);
        cmd.setOut(captured);
        cmd.setErr(captured);

        // 获取主线程用户
        ArgusUser currentUser = ArgusUserContext.getCurrentUser();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Integer> future = executor.submit(() -> {
            // 传递用户给子线程使用
            ArgusUserContext.setCurrentUser(currentUser);
            return cmd.execute(args);
        });

        long startTime = System.currentTimeMillis();
        try {

            int exitCode = future.get(timeoutSec, TimeUnit.SECONDS);
            long costTime = System.currentTimeMillis() - startTime;
            String output = baos.toString(StandardCharsets.UTF_8.name());
            return exitCode == 0
                    ? ExecuteResult.success(output, costTime)
                    : ExecuteResult.failed(output, costTime);
        } catch (TimeoutException e) {
            future.cancel(true);
            return ExecuteResult.failed("命令执行超时（超过 " + timeoutSec + " 秒）");
        } catch (Exception e) {
            long costTime = System.currentTimeMillis() - startTime;
            captured.flush();
            String output = baos.toString().trim();
            return output.isEmpty() ? ExecuteResult.failed(e.getMessage(), costTime) : ExecuteResult.failed(output, costTime);
        } finally {
            executor.shutdownNow();
        }
    }
}
