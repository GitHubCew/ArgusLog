package githubcew.arguslog.core.cache;


import githubcew.arguslog.config.ArgusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Argus Cache 管理器
 *
 * @author chenenwei
 */
@Component
public class ArgusCacheManager implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ArgusCacheManager.class);


    private final ArgusProperties argusProperties;

    // 定时任务执行器
    private final ScheduledExecutorService scheduler;

    /**
     * 构造方法
     *
     * @param argusProperties 配置
     */
    @Autowired
    public ArgusCacheManager(ArgusProperties argusProperties) {
        this.argusProperties = argusProperties;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "[Argus-Cache-Thread]");
            t.setDaemon(false);
            return t;
        });
    }

    /**
     * 启动定时清理任务
     */
    public void start() {
        scheduler.scheduleAtFixedRate(this::cleanExpiredCredentials,
                argusProperties.getTokenFlushTime(), argusProperties.getTokenFlushTime(), TimeUnit.SECONDS);
        log.info("【Argus => Started Argus Cache Manager...】");
    }

    /**
     * 清理过期凭证
     */
    private void cleanExpiredCredentials() {
        try {
            ArgusCache.clearExpiredToken();
            if (log.isDebugEnabled()) {
                log.debug("【Argus => Cleaning expired credentials...remain user: {}】", ArgusCache.countOnlineUser());
            }
        } catch (Exception e) {
            log.error("【Argus => Error occurred while cleaning expired credentials】", e);
            // 这里可以添加重启逻辑
            restartSchedulerIfNeeded();
        }
    }

    /**
     * 检查并重启调度器
     */
    private void restartSchedulerIfNeeded() {
        if (scheduler.isShutdown()) {
            log.warn("【Argus => Scheduler is shutdown, attempting to restart...】");
            // 创建新的调度器
            ScheduledExecutorService newScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "[Argus-Cache-Thread-Restarted]");
                t.setDaemon(false);
                return t;
            });

            // 重新启动任务
            newScheduler.scheduleAtFixedRate(this::cleanExpiredCredentials,
                    argusProperties.getTokenFlushTime(), argusProperties.getTokenFlushTime(), TimeUnit.SECONDS);

            if (log.isDebugEnabled()) {
                log.debug("【Argus => Scheduler restarted successfully】");
            }
        }
    }

    /**
     * afterPropertiesSet
     *
     * @throws Exception 异常
     */
    @Override
    public void afterPropertiesSet() throws Exception {
        start();
    }

    /**
     * 销毁方法
     *
     * @throws Exception 异常
     */
    @Override
    public void destroy() throws Exception {
        shutdown();
    }

    /**
     * 关闭线程
     */
    public void shutdown() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}