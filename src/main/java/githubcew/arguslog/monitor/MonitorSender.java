package githubcew.arguslog.monitor;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.config.ArgusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.*;

/**
 * 监控发送器
 *
 * @author chenenwei
 */

public class MonitorSender {

    private static final Logger log = LoggerFactory.getLogger(MonitorSender.class);

    private ArgusProperties argusProperties;
    private ThreadPoolExecutor scheduler;

    private volatile boolean isStarted = false;


    /**
     * 初始化方法
     */
    public void init() {
        if (isStarted) {
            return;
        }
        this.argusProperties = ContextUtil.getBean(ArgusProperties.class);
        this.scheduler = new ThreadPoolExecutor(
                argusProperties.getThreadCoreNum(),
                argusProperties.getThreadNum(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(argusProperties.getMaxWaitQueueSize()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        isStarted = true;
    }

    /**
     * 提交监控任务
     * @param task 任务
     */
    public void submit(Runnable task) {
        scheduler.execute(task);
    }

    /**
     * 提交有返回值的监控任务
     * @param task 任务
     * @param <T> 类型
     * @return  Future
     */
    public <T> Future<T> submit(Callable<T> task) {
        return scheduler.submit(task);
    }

    /**
     * 获取线程池状态信息
     * @return  String
     */
    public String getPoolStatus() {
        return String.format("活跃线程: %d, 队列大小: %d, 完成任务: %d",
                scheduler.getActiveCount(),
                scheduler.getQueue().size(),
                scheduler.getCompletedTaskCount());
    }
}