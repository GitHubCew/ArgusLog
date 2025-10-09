package githubcew.arguslog.monitor;

import githubcew.arguslog.monitor.trace.asm.MethodCallInfo;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.Date;
import java.util.List;
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

    private Date date;

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
         * @param startMethod    开始方法
         * @param methodCalls    方法调用信息
         * @param showFullClassName 是否显示完整类名
         */
        public Trace(long colorThreshold, int maxDepth, Method startMethod, Set<MethodCallInfo> methodCalls, boolean showFullClassName) {
            this.colorThreshold = colorThreshold;
            this.maxDepth = maxDepth;
            this.startMethod = startMethod;
            this.methodCalls = methodCalls;
            this.showFullClassName = showFullClassName;
        }
    }

    @Data
    public static class Sql {

        /**
         * 耗时阈值（ms）
         */
        private Long threshold;

        /**
         * 包名
         */
        private String packageName;

        /**
         * 类型名
         */
        private String className;

        /**
         * 方法名列表
         */
        private List<String> methodNames;

        public Sql() {
        }

        public Sql(Long threshold, String packageName, String className, List<String> methodNames) {
            this.threshold = threshold;
            this.packageName = packageName;
            this.className = className;
            this.methodNames = methodNames;
        }
    }
}
