package githubcew.arguslog.monitor.trace.asm;

import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.common.util.ContextUtil;
import lombok.SneakyThrows;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * 方法调用链提取器，基于 ASM 实现字节码分析。
 * 支持接口实现类、继承方法、代理类的调用链追踪。
 *
 * @author chenenwei
 */
public class AsmMethodCallExtractor {

    private static final Logger log = LoggerFactory.getLogger(AsmMethodCallExtractor.class);

    /**
     * 私有构造函数，防止实例化。
     */
    private AsmMethodCallExtractor() {
    }

    public static Set<MethodCallInfo> extractNestedCustomMethodCalls (Method method, Set<String> includePackages, Set<String> excludePackages) throws ClassNotFoundException {

        return extractNestedCustomMethodCalls(method.getDeclaringClass(),
                method.getName(),
                Type.getMethodDescriptor(method),
                includePackages,
                excludePackages);
    }
    /**
     * 递归提取指定方法的所有自定义方法调用链（支持接口与继承）。
     * 使用广度优先搜索（BFS）遍历调用图，深度逐层递增。
     *
     * @param targetClass      目标类（可以是接口或实现类）
     * @param targetMethodName 目标方法名
     * @param targetMethodDesc 目标方法描述符（ASM 格式）
     * @param includePackages  需要包含的包名前缀集合（用于过滤调用）
     * @return 所有方法调用信息的集合
     * @throws ClassNotFoundException 如果类无法加载
     */
    public static Set<MethodCallInfo> extractNestedCustomMethodCalls(Class<?> targetClass,
                                                                     String targetMethodName,
                                                                     String targetMethodDesc,
                                                                     Set<String> includePackages,
                                                                     Set<String> excludePackages) throws ClassNotFoundException {
        // 已处理的方法标识集合，防止重复分析
        Set<String> processedMethods = new HashSet<>();
        // 存储所有提取到的方法调用信息
        Set<MethodCallInfo> allCalls = new HashSet<>();
        // 使用队列实现广度优先分析
        Queue<MethodCallInfo> workQueue = new LinkedList<>();

        // 将类名中的 '.' 转换为 '/'（JVM 内部格式）
        String rootClassInternal = CommonUtil.toSlash(targetClass.getName());
        // 生成方法唯一标识 key
        String rootKey = genKey(rootClassInternal, targetMethodName, targetMethodDesc);

        // 根节点标识
        String root = "ROOT";

        // 创建根节点（入口方法），深度为0
        MethodCallInfo rootCall = new MethodCallInfo(
                root, root,
                rootClassInternal, targetMethodName, targetMethodDesc,
                false, rootClassInternal, rootClassInternal, -1, 0
        );
        // 添加根调用
        allCalls.add(rootCall);
        // 标记为已处理
        processedMethods.add(rootKey);
        // 加入工作队列
        workQueue.offer(rootCall);

        // 开始 BFS 循环处理每一层调用
        while (!workQueue.isEmpty()) {
            MethodCallInfo caller = workQueue.poll();

            // 处理根节点（无实际类）
            if (root.equals(caller.getCallerClass())) {
                try (InputStream in = targetClass.getResourceAsStream("/" + rootClassInternal + ".class")) {
                    if (in == null) continue;
                    ClassReader reader = new ClassReader(in);
                    ClassNode classNode = new ClassNode();
                    reader.accept(classNode, ClassReader.EXPAND_FRAMES);

                    // 获取被调用类的 Class 对象
                    Class<?> calledClass = Class.forName(CommonUtil.toDot(caller.getCalledClass()));

                    // 在类及其继承链中查找目标方法
                    MethodNodeWithInheritance methodWithInheritance = findMethodNodeInHierarchy(
                            calledClass.getClassLoader(),
                            caller.getCalledClass(),
                            caller.getCalledMethod(),
                            caller.getCalledMethodDesc()
                    );

                    if (!Objects.isNull(methodWithInheritance)) {
                        MethodNode methodNode = methodWithInheritance.methodNode;
                        boolean inherited = methodWithInheritance.inherited;
                        String actualDefinedClass = methodWithInheritance.definedInClass;
                        // 提取根方法的行号
                        int lineNumber = getMethodDeclarationLine(methodNode);
                        // 更新根节点的行号
                        ((MethodCallInfo) allCalls.toArray()[0]).setLineNumber(lineNumber);

                        // 提取该方法调用的子方法（深度=1）
                        Set<MethodCallInfo> calls = extractCallsFromMethodNode(
                                methodNode,
                                rootClassInternal,
                                targetMethodName,
                                includePackages,
                                excludePackages,
                                1,
                                inherited,
                                actualDefinedClass
                        );
                        // 设置实现类
                        for (MethodCallInfo call : calls) {
                            Class<?> specificClass = getSpecificClass(call.getCalledClass());
                            if (Objects.isNull(specificClass)) {
                                continue;
                            }
                            // 这里需要更新调用链中当前方法的继承状态
                            // 由于调用链是层层传递的，我们需要找到对应的调用记录并更新
                            updateInheritanceInCallChain(allCalls, call, inherited);
                            call.setSubCalledClass(CommonUtil.toSlash(specificClass.getName()));
                        }
                        // 添加到结果和队列中
                        addCalls(calls, allCalls, workQueue, processedMethods, calledClass.getClassLoader());
                    }
                } catch (IOException e) {
                    if (log.isDebugEnabled()) {
                        log.error("读取类失败: " + targetClass.getName(), e);
                    }
                }
            } else {
                // 处理普通调用节点
                String calledClass = caller.getSubCalledClass();
                String calledMethod = caller.getCalledMethod();
                String calledDesc = caller.getCalledMethodDesc();
                int nextDepth = caller.getDepth() + 1;

                try {
                    // 获取实际的目标类（支持接口、抽象类、代理）
                    Class<?> specificClass = getSpecificClass(calledClass);
                    if (Objects.isNull(specificClass)) {
                        continue;
                    }
                    // 类名转内部格式
                    String specificClassSlash = CommonUtil.toSlash(specificClass.getName());
                    // 构建 .class 文件路径
                    String resourcePath = specificClassSlash + ".class";
                    // 加载字节码流
                    if (Objects.isNull(specificClass.getClassLoader())) {
                        continue;
                    }
                    InputStream in = specificClass.getClassLoader().getResourceAsStream(resourcePath);
                    if (in == null) {
                        continue;
                    }

                    try (InputStream inputStream = in) {
                        ClassReader reader = new ClassReader(inputStream);
                        ClassNode classNode = new ClassNode();
                        reader.accept(classNode, ClassReader.EXPAND_FRAMES);

                        // 在类及其父类中查找方法

                        MethodNodeWithInheritance methodWithInheritance = findMethodNodeInHierarchy(
                                specificClass.getClassLoader(),
                                calledClass,
                                caller.getCalledMethod(),
                                caller.getCalledMethodDesc()
                        );
                        if (methodWithInheritance != null) {
                            MethodNode methodNode = methodWithInheritance.methodNode;
                            boolean inherited = methodWithInheritance.inherited;
                            String actualDefinedClass = methodWithInheritance.definedInClass;
                            Set<MethodCallInfo> calls = extractCallsFromMethodNode(
                                    methodNode,
                                    specificClassSlash,
                                    calledMethod,
                                    includePackages,
                                    excludePackages,
                                    nextDepth,
                                    inherited,
                                    actualDefinedClass
                            );
                            // 更新调用链中的继承信息
                            for (MethodCallInfo call : calls) {
                                // 这里需要更新调用链中当前方法的继承状态
                                // 由于调用链是层层传递的，我们需要找到对应的调用记录并更新
                                updateInheritanceInCallChain(allCalls, call, inherited);

                                Class<?> callSpecificClass = getSpecificClass(call.getCalledClass());
                                call.setSubCalledClass(CommonUtil.toSlash(callSpecificClass.getName()));
                            }
                            // 添加到结果中
                            addCalls(calls, allCalls, workQueue, processedMethods, specificClass.getClassLoader());

                        } else {
                            if (log.isDebugEnabled()) {
                                log.error("未找到方法: " + specificClass.getName() + "." + calledMethod + calledDesc);
                            }
                        }
                    }
                } catch (Exception e) {
                    if (log.isDebugEnabled()) {
                        log.error("分析失败: " + calledClass  , e);
                    }
                }
            }
        }

        return allCalls;
    }

    private static void updateInheritanceInCallChain(Set<MethodCallInfo> allCalls,
                                                     MethodCallInfo currentCall,
                                                     boolean inherited) {
        // 查找与 currentCall 匹配的调用记录
        for (MethodCallInfo call : allCalls) {
            if (call.getCalledClass().equals(currentCall.getCalledClass()) &&
                    call.getCalledMethod().equals(currentCall.getCalledMethod()) &&
                    call.getCalledMethodDesc().equals(currentCall.getCalledMethodDesc())) {
                call.setInherited(inherited);
                break;
            }
        }
    }

    /**
     * 从 MethodNode 中提取方法内的所有自定义方法调用。
     *
     * @param methodNode      方法节点
     * @param callerClass     调用类（内部名）
     * @param callerMethod    调用方法名
     * @param includePackages 包过滤集合
     * @param depth           调用深度
     * @return 方法调用信息集合
     */
    private static Set<MethodCallInfo> extractCallsFromMethodNode(MethodNode methodNode,
                                                                  String callerClass,
                                                                  String callerMethod,
                                                                  Set<String> includePackages,
                                                                  Set<String> excludePackages,
                                                                  int depth,
                                                                  boolean inherited,
                                                                  String actualDefinedClass) {
        Set<MethodCallInfo> calls = new HashSet<>();
        ArgusMethodCallVisitor visitor = new ArgusMethodCallVisitor(
                includePackages, excludePackages, callerClass, callerMethod, calls, depth, inherited, actualDefinedClass);
        methodNode.accept(visitor);
        return calls;
    }

    /**
     * 获取方法的第一行行号（从 LineNumberTable 中提取）
     *
     * @param methodNode 方法节点
     * @return 行号，未找到返回 -1
     */
    /**
     * 获取方法的第一行行号（从 LineNumberTable 中提取）
     *
     * @param methodNode 方法节点
     * @return 行号，未找到返回 -1
     */
    /**
     * 通过 ASM 获取方法的声明行号（取 LineNumberTable 中最小行号）
     */
    public static int getMethodDeclarationLine(MethodNode methodNode) {
        if (methodNode.instructions == null) return -1;

        int minLine = Integer.MAX_VALUE;
        AbstractInsnNode node = methodNode.instructions.getFirst();
        while (node != null) {
            if (node instanceof LineNumberNode) {
                int line = ((LineNumberNode) node).line;
                if (line > 0) {
                    minLine = Math.min(minLine, line);
                }
            }
            node = node.getNext();
        }
        return minLine == Integer.MAX_VALUE ? -1 : minLine;
    }

    /**
     * 在类及其父类继承链中查找指定方法（支持继承）。
     *
     * @param classLoader 类加载器
     * @param className   类名（内部格式，如 java/lang/Object）
     * @param name        方法名
     * @param desc        方法描述符
     * @return 找到的方法节点，未找到返回 null
     * @throws IOException 如果类文件读取失败
     */
    private static MethodNodeWithInheritance findMethodNodeInHierarchy(ClassLoader classLoader,
                                                                       String className,
                                                                       String name,
                                                                       String desc) throws IOException {
        String currentClass = className;
        String originalClass = className;
        String definedInClass = null;

        while (currentClass != null) {
            try (InputStream in = classLoader.getResourceAsStream(CommonUtil.toSlash(currentClass) + ".class")) {
                if (in == null) {
                    break;
                }

                ClassReader reader = new ClassReader(in);
                ClassNode classNode = new ClassNode();
                reader.accept(classNode, ClassReader.EXPAND_FRAMES);

                // 遍历当前类的方法
                for (MethodNode method : classNode.methods) {
                    if (method.name.equals(name) && method.desc.equals(desc)) {
                        definedInClass = currentClass; // 记录方法实际定义的类
                        boolean inherited = !currentClass.equals(originalClass);
                        return new MethodNodeWithInheritance(method, inherited, definedInClass);
                    }
                }

                // 继续查找父类
                currentClass = classNode.superName;

                // 到达 Object 类停止
                if ("java/lang/Object".equals(currentClass)) {
                    break;
                }
            }
        }

        return null;
    }

    /**
     * 添加新的方法调用到结果集和工作队列中，并去重。
     *
     * @param newCalls         新的方法调用集合
     * @param allCalls         所有调用集合
     * @param workQueue        工作队列
     * @param processedMethods 已处理的方法 key 集合
     */
    private static void addCalls(Set<MethodCallInfo> newCalls,
                                 Set<MethodCallInfo> allCalls,
                                 Queue<MethodCallInfo> workQueue,
                                 Set<String> processedMethods,
                                 ClassLoader classLoader) {
        for (MethodCallInfo call : newCalls) {
            String key = genKey(call.getCalledClass(), call.getCalledMethod(), call.getCalledMethodDesc()) + call.getDepth() + call.getLineNumber();

            if (!processedMethods.contains(key)) {
                // 立即查找方法的实际定义类
                try {
                    MethodNodeWithInheritance methodInfo = findMethodNodeInHierarchy(
                            classLoader,
                            call.getCalledClass(),
                            call.getCalledMethod(),
                            call.getCalledMethodDesc()
                    );

                    if (methodInfo != null) {
                        call.setActualDefinedClass(methodInfo.definedInClass);
                        call.setInherited(methodInfo.inherited);
                    }
                } catch (IOException e) {
                    // 处理异常
                }

                processedMethods.add(key);
                workQueue.offer(call);
            }
            allCalls.add(call);
        }
    }

    /**
     * 获取实际的目标类（支持接口、抽象类、代理类）。
     *
     * @param calledClass 被调用类名（内部格式）
     * @return 实际的目标类，若无法获取则返回 null
     */
    private static Class<?> getSpecificClass(String calledClass) {
        Class<?> specificClass = null;
        try {
            Object bean = null;
            specificClass = Class.forName(CommonUtil.toDot(calledClass));

            // 如果是接口或抽象类，尝试从 Spring 获取实现
            if (specificClass.isInterface() || Modifier.isAbstract(specificClass.getModifiers())) {
                try {
                    bean = ContextUtil.getBean(specificClass);
                    specificClass = AopProxyUtils.ultimateTargetClass(bean);
                } catch (Exception e) {
                    System.err.println("无法获取实现类，跳过: " + specificClass);
                }
            }

            // 处理代理类
            if (AopUtils.isAopProxy(specificClass)) {
                specificClass = AopProxyUtils.ultimateTargetClass(bean != null ? bean : specificClass);
            }
        } catch (ClassNotFoundException e) {
            System.err.println("类未找到: " + calledClass);
        }
        return specificClass;
    }

    /**
     * 生成方法的唯一标识 key。
     *
     * @param className  类名（内部格式）
     * @param methodName 方法名
     * @param methodDesc 方法描述符
     * @return 唯一 key
     */
    public static String genKey(String className, String methodName, String methodDesc) {
        return className + "#" + methodName + methodDesc;
    }

    /**
     * ASM MethodVisitor 实现类，用于提取方法内的调用指令。
     */
    private static class ArgusMethodCallVisitor extends MethodVisitor {
        private final Set<String> includePackages = new HashSet<>();
        private final Set<String> excludePackages = new HashSet<>();
        private final String callerClass;
        private final String callerMethod;
        private final Set<MethodCallInfo> methodCalls;
        private final int currentDepth;
        private int currentLine = -1;
        private boolean callerInherited;
        private String actualDefinedClass;
        /**
         * 构造方法调用访问器。
         *
         * @param includePackages 包过滤集合
         * @param callerClass     调用类
         * @param callerMethod    调用方法
         * @param methodCalls     存储结果的集合
         * @param currentDepth    当前调用深度
         */
        public ArgusMethodCallVisitor(Set<String> includePackages,
                                      Set<String> excludePackages,
                                      String callerClass,
                                      String callerMethod,
                                      Set<MethodCallInfo> methodCalls,
                                      int currentDepth,
                                      boolean callerInherited,
                                      String actualDefinedClass) {
            super(Opcodes.ASM9);
            if (!Objects.isNull(includePackages)) {
                this.includePackages.addAll(includePackages);
            }
            if (!Objects.isNull(excludePackages)) {
                this.excludePackages.addAll(excludePackages);
            }
            this.callerClass = callerClass;
            this.callerMethod = callerMethod;
            this.methodCalls = methodCalls;
            this.currentDepth = currentDepth;
            this.callerInherited = callerInherited;
            this.actualDefinedClass = actualDefinedClass;
        }

        /**
         * 访问方法调用指令（如 invokevirtual, invokeinterface 等）。
         */
        @SneakyThrows
        @Override
        public void visitMethodInsn(int opcode, String owner, String name,
                                    String descriptor, boolean isInterface) {

            // 跳过非业务方法
            if (isTrivialMethod(name, descriptor)) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }

            if (isFilter(owner)) {
                MethodCallInfo callInfo = new MethodCallInfo(
                        callerClass, callerMethod,
                        owner, name, descriptor, callerInherited,
                        actualDefinedClass,
                        owner, currentLine, currentDepth
                );
                methodCalls.add(callInfo);
            }
            super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
        }

        /**
         * 访问行号指令，用于记录调用发生的源码行。
         */
        @Override
        public void visitLineNumber(int line, org.objectweb.asm.Label start) {
            currentLine = line;
            super.visitLineNumber(line, start);
        }

        /**
         * 访问 invokedynamic 指令，处理 Lambda、方法引用等动态调用。
         */
        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor,
                                           Handle bootstrapMethodHandle,
                                           Object... bootstrapMethodArguments) {
            for (Object arg : bootstrapMethodArguments) {
                if (arg instanceof Handle) {
                    Handle handle = (Handle) arg;
                    int tag = handle.getTag();
                    if ((tag == Opcodes.H_INVOKESTATIC ||
                            tag == Opcodes.H_INVOKESPECIAL ||
                            tag == Opcodes.H_INVOKEVIRTUAL ||
                            tag == Opcodes.H_INVOKEINTERFACE) &&
                            isFilter(handle.getOwner())) {

                        MethodCallInfo callInfo = new MethodCallInfo(
                                callerClass, callerMethod,
                                handle.getOwner(), handle.getName(), handle.getDesc(),
                                callerInherited, actualDefinedClass,
                                handle.getOwner(), currentLine, currentDepth
                        );
                        methodCalls.add(callInfo);
                    }
                }
            }
            super.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments);
        }

        /**
         * 判断是否为自定义方法（在指定包内 或 不在指定包内）。
         *
         * @param className 类名（内部格式）
         * @return 是否匹配
         */
        private boolean isFilter(String className) {

            if (className.contains("/")) {
                className = className.replace("/", ".");
            }
            // 在排除包excludePackages中且不在包includePackages中的过滤掉
            for (String excludePackage : excludePackages) {
                if (className.startsWith(excludePackage)) {
                    for (String includePackage : includePackages) {
                        if (className.startsWith(includePackage)) {
                            return true;
                        }
                    }
                    return false;
                }
            }

            for (String includedPackage : includePackages) {
                if (className.startsWith(includedPackage)) {
                    return true;
                }
            }

            return false;
        }

        /**
         * 判断是否为“无意义方法”：getter、setter、toString、equals、hashCode
         *
         * @param name       方法名
         * @param descriptor 方法描述符
         * @return 是否为应跳过的无意义方法
         */
        private static boolean isTrivialMethod(String name, String descriptor) {
            // 跳过 <init> 和 <clinit>
            if ("<init>".equals(name) || "<clinit>".equals(name)) {
                return true;
            }

            Type methodType = Type.getMethodType(descriptor);
            Type[] args = methodType.getArgumentTypes();

            // -------------------------------
            // 1. Getter / Setter
            // -------------------------------
            if (name.startsWith("set") && args.length == 1 && Character.isUpperCase(name.charAt(3))) {
                if (isStandardSetter(name)) {
                    return true;
                }
            }

            if (args.length == 0) {
                if (name.startsWith("get") && name.length() > 3 && Character.isUpperCase(name.charAt(3))) {
                    return true;
                }
//                if (name.startsWith("is") && name.length() > 2 && Character.isUpperCase(name.charAt(2))) {
//                    Type returnType = methodType.getReturnType();
//                    return returnType.equals(Type.BOOLEAN_TYPE) ||
//                            "Ljava/lang/Boolean;".equals(returnType.getDescriptor());
//                }
            }

            // -------------------------------
            // 2. toString()
            // -------------------------------
            if ("toString".equals(name) && args.length == 0) {
                Type returnType = methodType.getReturnType();
                return "Ljava/lang/String;".equals(returnType.getDescriptor());
            }

            // -------------------------------
            // 3. hashCode()
            // -------------------------------
            if ("hashCode".equals(name) && args.length == 0) {
                Type returnType = methodType.getReturnType();
                return returnType.equals(Type.INT_TYPE);
            }

            // -------------------------------
            // 4. equals(Object)
            // -------------------------------
            if ("equals".equals(name) && args.length == 1) {
                Type returnType = methodType.getReturnType();
                return returnType.equals(Type.BOOLEAN_TYPE) &&
                        "Ljava/lang/Object;".equals(args[0].getDescriptor());
            }

            return false;
        }

        private static boolean isStandardSetter(String name) {
            if (!name.startsWith("set") || name.length() <= 3) return false;
            if (!Character.isUpperCase(name.charAt(3))) return false;

            String property = name.substring(3);
            String lower = property.toLowerCase();

            // 排除含业务动词的
            if (lower.contains("from") || lower.contains("with") || lower.contains("and") ||
                    lower.contains("by") || lower.contains("for") || lower.contains("using") ||
                    lower.contains("via") || lower.contains("when") || lower.contains("after")) {
                return false;
            }

            // 必须是合法的标识符：首字母大写，其余字母数字
            return property.matches("^[A-Z][a-zA-Z]*$");
        }
    }

    private static class MethodNodeWithInheritance {
        MethodNode methodNode;
        boolean inherited;
        String definedInClass; // 方法实际定义的类

        MethodNodeWithInheritance(MethodNode methodNode, boolean inherited, String definedInClass) {
            this.methodNode = methodNode;
            this.inherited = inherited;
            this.definedInClass = definedInClass;
        }
    }
}