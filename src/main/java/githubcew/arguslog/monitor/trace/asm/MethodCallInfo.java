package githubcew.arguslog.monitor.trace.asm;

import lombok.Data;

/**
 * 方法调用信息
 *
 * @author chenenwei
 */
@Data
public class MethodCallInfo {

    /**
     * 调用类名
     */
    private String callerClass;
    /**
     * 调用方法
     */
    private String callerMethod;
    /**
     * 被调用类名
     */
    private String calledClass;

    /**
     * 被调用方法
     */
    private String calledMethod;

    /**
     * 被调用方法签名描述
     */
    private String calledMethodDesc;

    /**
     * 被调用的子类
     */
    private String subCalledClass;

    /**
     * 方法实际定义的类
     */
    private String actualDefinedClass;

    /**
     * 是否是继承方法
     */
    private boolean inherited;
    /**
     * 行号
     */
    private int lineNumber;
    /**
     * 深度
     */
    private int depth = 0;

    /**
     * 构造方法
     * @param callerClass 调用类
     * @param callerMethod 调用方法
     * @param calledClass 被调用类
     * @param calledMethod 被调用方法
     * @param calledMethodDesc 被调用方法签名描述
     * @param isInherited 是否是继承方法
     * @param actualDefinedClass 方法实际定义的类
     * @param subCalledClass 被调的子类
     * @param lineNumber 行号
     * @param depth 深度
     */
    public MethodCallInfo(String callerClass, String callerMethod,
                          String calledClass, String calledMethod,
                          String calledMethodDesc,  boolean isInherited,
                          String actualDefinedClass, String subCalledClass,
                          int lineNumber,
                          int depth) {
        this.callerClass = callerClass;
        this.callerMethod = callerMethod;
        this.calledClass = calledClass;
        this.calledMethod = calledMethod;
        this.calledMethodDesc = calledMethodDesc;
        this.inherited = isInherited;
        this.actualDefinedClass = actualDefinedClass;
        this.subCalledClass = subCalledClass;
        this.lineNumber = lineNumber;
        this.depth = depth;
    }

}