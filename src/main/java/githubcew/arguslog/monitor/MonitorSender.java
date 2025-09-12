package githubcew.arguslog.monitor;

import githubcew.arguslog.config.ArgusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * 监控发送器
 *
 * @author chenenwei
 */

@Component
public class MonitorSender implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MonitorSender.class);

    private final ArgusProperties argusProperties;
    private final ThreadPoolExecutor scheduler;


    /**
     * 构造方法
     * @param argusProperties argus 配置
     */
    @Autowired
    public MonitorSender(ArgusProperties argusProperties) {
        this.argusProperties = argusProperties;
        this.scheduler = new ThreadPoolExecutor(
                argusProperties.getThreadCoreNum(),
                argusProperties.getThreadNum(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(argusProperties.getMaxWaitQueueSize()),
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
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

    /**
     * 容器销毁
     * @throws Exception 异常
     */
    @Override
    public void destroy() throws Exception {
        // 优雅关闭线程池
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * bean属性设置
     * @throws Exception 异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        if (log.isDebugEnabled()) {
            log.debug("MonitorExecutor 初始化完成，线程池配置: " +
                    "核心线程数=" + argusProperties.getThreadCoreNum() +
                    ", 最大线程数=" + argusProperties.getThreadNum() +
                    ", 队列大小=" + argusProperties.getMaxWaitQueueSize());
        }
    }
}