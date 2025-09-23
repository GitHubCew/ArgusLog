package githubcew.arguslog.monitor;

import githubcew.arguslog.monitor.trace.asm.MethodCallInfo;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * 方法监测信息类
 *
 * @author chenenwei
 */
@Data
public class MonitorInfo {

    /**
     * 方法
     */
    private ArgusMethod argusMethod;
    /**
     * 请求头
     */
    private boolean header;

    /**
     * 请求ip
     */
    private boolean ip;
    /**
     * 请求参数
     */
    private boolean param;
    /**
     * 方法参数
     */
    private boolean methodParam;
    /**
     * 结果
     */
    private boolean result;
    /**
     * 时间
     */
    private boolean time;

    /**
     * 请求路径
     */
    private boolean url;
    /**
     * 请求接口路径
     */
    private boolean api;

    /**
     * 请求方法类型
     */
    private boolean type;

    /**
     * 实际请求接口方法
     */
    private boolean method;

    private Trace trace;

    @Data
    public static class Trace {
        /**
         * 耗时阈值
         */
        private long colorThreshold;

        /**
         * 最大深度
         */
        private int maxDepth;

        /**
         * 开始方法
         */
        private Method startMethod;

        /**
         * 方法调用信息
         */
        private Set<MethodCallInfo> methodCalls;

        /**
         * 是否显示完整类名
         */
        private boolean showFullClassName;

        /**
         * 构造方法
         */
        public Trace() {
        }

        /**
         * 构造方法
         *
         * @param colorThreshold 颜色阈值
         * @param maxDepth       最大深度
         * @param startMethod       开始方法
         * @param methodCalls    方法调用信息
         */
        public Trace(long colorThreshold, int maxDepth, Method startMethod, Set<MethodCallInfo> methodCalls, boolean showFullClassName) {
            this.colorThreshold = colorThreshold;
            this.maxDepth = maxDepth;
            this.startMethod = startMethod;
            this.methodCalls = methodCalls;
            this.showFullClassName = showFullClassName;
        }
    }

}
