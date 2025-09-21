package githubcew.arguslog.core.cache;


import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.config.ArgusProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Argus Cache 管理器
 *
 * @author chenenwei
 */
public class ArgusCacheManager {

    private static final Logger log = LoggerFactory.getLogger(ArgusCacheManager.class);


    private ArgusProperties argusProperties;

    // 定时任务执行器
    private ScheduledExecutorService scheduler;

    private volatile boolean isStarted = false;

    /**
     * 初始化方法
     */
    private void init () {
        if (isStarted) {
            return;
        }
        this.argusProperties = ContextUtil.getBean(ArgusProperties.class);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "[Argus-Cache-Thread]");
            t.setDaemon(false);
            return t;
        });
        isStarted = true;
    }

    /**
     * 启动定时清理任务
     */
    public void start() {
        init();
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
}