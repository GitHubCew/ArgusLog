package githubcew.arguslog.monitor.trace;// src/main/java/com/yourcompany/agent/BytecodeRedefiner.java

import net.bytebuddy.agent.ByteBuddyAgent;

import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;

/**
 * 类重定义
 *
 * @author chenenwei
 */
public class ArgusClassRedefiner {

    private volatile Instrumentation instrumentation;

    /**
     * 确保 Instrumentation 对象已初始化
     */
    private void ensureInstrumentation() {
        if (instrumentation == null) {
            synchronized (this) {
                if (instrumentation == null) {
                    this.instrumentation = ByteBuddyAgent.install();
                }
            }
        }
    }

    /**
     * 直接使用字节码数组重定义类
     *
     * @param targetClassName 要修改的类名，如 com.example.Service
     * @param newClassBytes   新的 class 字节码
     * @throws Exception 如果类未找到或 redefine 失败
     */
    public void redefine(String targetClassName, byte[] newClassBytes) throws Exception {
        ensureInstrumentation();

        // 获取当前上下文类加载器
        ClassLoader cl = Thread.currentThread().getContextClassLoader();

        // 加载目标类
        Class<?> targetClass;
        try {
            targetClass = cl.loadClass(targetClassName);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Target class not found: " + targetClassName, e);
        }

        // 构造 ClassDefinition 并重新定义
        ClassDefinition definition = new ClassDefinition(targetClass, newClassBytes);
        instrumentation.redefineClasses(definition);
    }
}