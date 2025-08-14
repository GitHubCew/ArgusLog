package githubcew.arguslog.core;


import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
/**
 * 缓存管理线程，定时清理过期缓存
 * @author chenenwei
 */
public class CacheThread {

    private final static long CACHE_FLUSH_TIME = 60;

    // 单例实例
    private static volatile CacheThread instance;

    // 定时任务执行器
    private final ScheduledExecutorService scheduler;

    private CacheThread() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "[Argus CacheThread Cleaning Expired KEY]");
            t.setDaemon(true); // 设置为守护线程
            return t;
        });
    }

    /**
     * 获取单例实例
     */
    public static CacheThread getInstance() {
        if (instance == null) {
            synchronized (CacheThread.class) {
                if (instance == null) {
                    instance = new CacheThread();
                }
            }
        }
        return instance;
    }

    /**
     * 启动定时清理任务
     */
    public void startCleanupTask() {
        // 每60秒执行一次清理
        scheduler.scheduleAtFixedRate(this::cleanExpiredCredentials,
                CACHE_FLUSH_TIME, CACHE_FLUSH_TIME, TimeUnit.SECONDS);
    }

    /**
     * 清理过期凭证
     */
    private void cleanExpiredCredentials() {
        Cache.removeCredentials(System.currentTimeMillis());
    }

}