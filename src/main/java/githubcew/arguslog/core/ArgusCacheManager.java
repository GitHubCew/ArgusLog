package githubcew.arguslog.core;


import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
/**
 * Argus Cache 管理器
 * @author chenenwei
 */
public class ArgusCacheManager {

    // 缓存凭证刷新时间（秒）
    private final static long INITIAL_DELAY = 0;
    private final static long FLUSH_PERIOD = 60;

    // 单例实例
    private static volatile ArgusCacheManager instance;

    // 定时任务执行器
    private final ScheduledExecutorService scheduler;

    private ArgusCacheManager() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "[Argus Cache Thread Cleaning Expired KEY]");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 获取单例实例
     */
    public static ArgusCacheManager getInstance() {
        if (instance == null) {
            synchronized (ArgusCacheManager.class) {
                if (instance == null) {
                    instance = new ArgusCacheManager();
                }
            }
        }
        return instance;
    }

    /**
     * 启动定时清理任务
     */
    public void start() {
        scheduler.scheduleAtFixedRate(this::cleanExpiredCredentials,
                INITIAL_DELAY, FLUSH_PERIOD, TimeUnit.SECONDS);
    }

    /**
     * 清理过期凭证
     */
    private void cleanExpiredCredentials() {
        ArgusCache.clearExpiredUser();
    }

}