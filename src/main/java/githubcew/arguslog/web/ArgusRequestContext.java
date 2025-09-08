package githubcew.arguslog.web;

import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.core.cmd.ColorWrapper;
import githubcew.arguslog.monitor.MonitorInfo;
import githubcew.arguslog.monitor.trace.asm.MethodCallInfo;
import lombok.Data;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * argus web请求上下文
 *
 * @author chenenwei
 */
public class ArgusRequestContext {

    /**
     * 请求方法
     */
    private static final ThreadLocal<Map<String, List<MethodInvocation>>> REQUEST_METHODS =
            ThreadLocal.withInitial(ConcurrentHashMap::new);

    /**
     * 调用栈
     */
    private static final ThreadLocal<Stack<MethodInvocation>> CALL_STACK =
            ThreadLocal.withInitial(Stack::new);

    /**
     * 请求id
     */
    private static final ThreadLocal<String> REQUEST_ID = new ThreadLocal<>();

    /**
     * 首次请求方法映射
     */
    private static final Map<String, Method> METHOD_SIGNATURE_TO_METHOD = new ConcurrentHashMap<>();

    /**
     * 方法签名
     */
    private static final Map<Method, String> METHOD_TO_SIGNATURE = new ConcurrentHashMap<>();

    /**
     * 调用树根节点
     */
    private static final ThreadLocal<MethodNode> CALL_TREE_ROOT = new ThreadLocal<>();

    /**
     * 当前节点指针（用于构建树）
     */
    private static final ThreadLocal<MethodNode> CURRENT_NODE = new ThreadLocal<>();

    /**
     * 调用计数器（用于区分相同方法的多次调用）
     */
    private static final ThreadLocal<Map<String, Integer>> INVOCATION_COUNTER =
            ThreadLocal.withInitial(ConcurrentHashMap::new);


    /**
     * 开始请求
     *
     * @param requestId 请求id
     */
    public static void startRequest(String requestId) {
        REQUEST_ID.set(requestId);
        REQUEST_METHODS.get().clear();
        CALL_STACK.get().clear();
        CALL_TREE_ROOT.set(null);
        CURRENT_NODE.set(null);
        INVOCATION_COUNTER.get().clear();
    }

    /**
     * 结束请求
     *
     * @return 结果
     */
    public static Map<String, Long> endRequest() {
        Map<String, Long> costMap = REQUEST_METHODS.get().values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toMap(MethodInvocation::getMethodSignature, MethodInvocation::getDuration));
        clear();
        return costMap;
    }

    /**
     * 获取开始方法
     * @param requestId 请求id
     * @return 方法
     */
    public static Method getStartMethod(String requestId) {
        return METHOD_SIGNATURE_TO_METHOD.get(requestId);

    }

    /**
     * 获取调用树根节点
     * @return 根节点
     */
    public static MethodNode getCallTree() {
        return CALL_TREE_ROOT.get();
    }

    /**
     * 获取树节点
     * @return MethodNode
     */
    public static MethodNode getMethodNode() {
        return CALL_TREE_ROOT.get();
    }

    /**
     * 递归构建树形字符串
     *
     * @param node 当前节点
     * @param depth 当前节点的深度
     * @param trace trace信息
     * @param parentIsLastList 父节点是否是最后一个节点的列表
     * @param methodCount 方法调用计数器
     * @return 树形字符串
     */
    public static String buildTreeString(MethodNode node,
                                         int depth,
                                         MonitorInfo.Trace trace,
                                         List<Boolean> parentIsLastList,
                                         Map<String, Integer> methodCount) {
        StringBuilder sb = new StringBuilder();

        // 构建前缀（竖线和缩进）
        for (int i = 0; i < depth; i++) {
            if (i < parentIsLastList.size() - 1) {
                sb.append(parentIsLastList.get(i) ? "    " : "│   ");
            }
        }

        // 构建当前节点的连接线
        if (depth > 0) {
            boolean isLast = parentIsLastList.get(parentIsLastList.size() - 1);
            sb.append(isLast ? "└── " : "├── ");
        }


        // 获取带参数的简化方法签名
        String signatureWithParams = getSignatureWithParams(node);

        // 获取行号
        String lineNumber = getLineNumber(node.getMethod(), depth, trace.getMethodCalls());

        sb.append(signatureWithParams);
        // 行号
        if (!lineNumber.isEmpty()) {
            sb.append("#").append(lineNumber);
        }
        sb.append(" [");
        if (node.getDuration() >= trace.getColorThreshold()) {
            sb.append(ColorWrapper.red(String.valueOf(node.getDuration())));
        } else {
            sb.append(node.getDuration());
        }
        sb.append("ms]");
        sb.append("\n");

        // 检查是否达到最大深度，如果是，不再递归
        if (depth >= trace.getMaxDepth()) {
            return sb.toString();
        }

        // 递归处理子节点
        List<MethodNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            boolean childIsLast = (i == children.size() - 1);
            List<Boolean> newParentIsLastList = new ArrayList<>(parentIsLastList);
            newParentIsLastList.add(childIsLast);
            // 显示超过1次则不显示
            String key = children.get(i).getSignature() + depth;
            methodCount.putIfAbsent(key, 0);
            Integer printCount = methodCount.get(key);
            if (Objects.isNull(printCount) || printCount > 0) {
                continue;
            }
            else {
                methodCount.put(key, printCount + 1);
            }
            sb.append(buildTreeString(children.get(i), depth + 1, trace, newParentIsLastList, methodCount));
        }

        return sb.toString();
    }

    /**
     * 获取带参数的简化方法签名（类名.方法名(参数类型)
     * 如果参数超过64个字符，则截取并在后面添加"..."
     *
     * @param node 节点
     * @return 简化方法签名
     */
    private static String getSignatureWithParams(MethodNode node) {
        String className = getSimpleClassName(node.getSignature());
        String methodName = getMethodName(node.getSignature());

        // 构建参数列表字符串
        String paramsString = buildParamsString(node);


        // 参数超过64时，前面参数保留完成
        if (paramsString.length() > 64) {
            int endIndex = paramsString.indexOf(",", 64);
            if (endIndex != -1) {
                paramsString = paramsString.substring(0, endIndex) + "...";
            }
        }

        return className + "." + methodName + "(" + paramsString + ")";
    }


    /**
     * 获取行号
     * @param method 方法
     * @param depth 深度
     * @param methodCalls 方法调用信息
     * @return 行号
     */
    public static String getLineNumber(Method method, int depth,  Set<MethodCallInfo> methodCalls) {

        String className = method.getDeclaringClass().getName().replace(".", "/");
        String methodName = method.getName();
        String methodDesc = Type.getMethodDescriptor(method);
        // 现在统一深度找
        Optional<MethodCallInfo> first = methodCalls.stream().filter(call ->
                (call.getSubCalledClass().equals(className) || call.getCalledClass().equals(className)) // 兼容jdk代理的类名，和调用接口名一致
                && call.getCalledMethod().equals(methodName)
                && call.getCalledMethodDesc().equals(methodDesc)
                && call.getDepth() == depth).findFirst();
        return first.map(methodCallInfo -> String.valueOf(methodCallInfo.getLineNumber())).orElse("");
    }

    /**
     * 构建参数列表字符串
     * @param node 节点
     * @return 参数列表字符串
     */
    private static String buildParamsString(MethodNode node) {
        if (node.getMethod() != null) {
            // 从Method对象获取参数类型
            Class<?>[] paramTypes = node.getMethod().getParameterTypes();
            if (paramTypes.length > 0) {
                List<String> paramTypeNames = new ArrayList<>();
                for (Class<?> paramType : paramTypes) {
                    paramTypeNames.add(getSimpleTypeName(paramType.getSimpleName()));
                }
                return String.join(",", paramTypeNames);
            }
        }

        // 尝试从签名中解析参数（如果签名包含参数信息）
        String signature = node.getSignature();
        if (signature.contains("(") && signature.contains(")")) {
            int start = signature.indexOf('(') + 1;
            int end = signature.indexOf(')');
            if (start < end) {
                return signature.substring(start, end);
            }
        }

        return ""; // 无参数
    }

    /**
     * 截断过长的签名
     * @param className 类名
     * @param methodName 方法名
     * @param paramsString 参数列表字符串
     * @return 截断后的签名
     */
    private static String truncateSignature(String className, String methodName, String paramsString) {
        // 计算基础部分长度：className.methodName().#invocationIndex
        int baseLength = className.length() + methodName.length() + 3; // +3 for "()."

        // 尝试包含部分参数
        String[] params = paramsString.split(",");
        StringBuilder truncatedParams = new StringBuilder();
        int currentLength = baseLength;

        for (String param : params) {
            if (currentLength + param.length() + 1 > 64) { // +1 for comma
                if (truncatedParams.length() > 0) {
                    truncatedParams.append(",...");
                } else {
                    // 即使第一个参数也超长，至少显示部分
                    if (param.length() > 10) {
                        truncatedParams.append(param.substring(0, 10)).append("...");
                    } else {
                        truncatedParams.append(param).append(",...");
                    }
                }
                break;
            }

            if (truncatedParams.length() > 0) {
                truncatedParams.append(",");
                currentLength++;
            }

            truncatedParams.append(param);
            currentLength += param.length();
        }

        return className + "." + methodName + "(" + truncatedParams.toString() + ")";
    }

    /**
     * 从完整签名中提取简单类名
     * @param signature 完整签名
     * @return 简单类名
     */
    private static String getSimpleClassName(String signature) {
        if (signature == null) return "";

        // 处理格式：package/Class#method 或 package.Class#method
        int hashIndex = signature.indexOf('#');
        if (hashIndex == -1) return signature;

        String classPart = signature.substring(0, hashIndex);

        // 提取简单类名
        int lastSlash = classPart.lastIndexOf('/');
        if (lastSlash != -1) {
            return classPart.substring(lastSlash + 1);
        }

        int lastDot = classPart.lastIndexOf('.');
        if (lastDot != -1) {
            return classPart.substring(lastDot + 1);
        }

        return classPart;
    }

    /**
     * 从完整签名中提取方法名
     * @param signature 完整签名
     * @return 方法名
     */
    private static String getMethodName(String signature) {
        if (signature == null) return "";

        int hashIndex = signature.indexOf('#');
        if (hashIndex == -1) return signature;

        String methodPart = signature.substring(hashIndex + 1);

        // 如果方法名包含参数信息，去掉参数部分
        int parenIndex = methodPart.indexOf('(');
        if (parenIndex != -1) {
            return methodPart.substring(0, parenIndex);
        }

        return methodPart;
    }

    /**
     * 简化类型名称
     *
     * @param typeName 类型名称
     * @return 简化后的类型名称
     */
    private static String getSimpleTypeName(String typeName) {
        // 简化常见的类型名称
        switch (typeName) {
            case "String":
                return "String";
            case "Integer":
                return "int";
            case "Long":
                return "long";
            case "Double":
                return "double";
            case "Float":
                return "float";
            case "Boolean":
                return "boolean";
            case "Character":
                return "char";
            case "Byte":
                return "byte";
            case "Short":
                return "short";
            default:
                // 对于其他类型，去掉包名
                int lastDot = typeName.lastIndexOf('.');
                return lastDot != -1 ? typeName.substring(lastDot + 1) : typeName;
        }
    }

//    /**
//     * 获取树统计信息
//     *
//     * @return 树
//     */
//    public static String getTreeStatistics() {
//        MethodNode root = CALL_TREE_ROOT.get();
//        if (root == null) {
//            return "";
//        }
//
//        return "Argus Trace => \n" +
//                "Root: " + getSignatureWithParams(root) + "\n" +
//                "Tree:\n" + getFormattedTree();
//    }

    /**
     * 获取开始方法
     * @param method 方法
     */
    public static void startMethod(Method method) {
        if (Objects.isNull(method)) {
            return;
        }
        String requestId = REQUEST_ID.get();
        if (Objects.isNull(requestId) || requestId.isEmpty()) {
            return;
        }
        String methodSignature = CommonUtil.toSlash(method.getDeclaringClass().getName() + "#" + method.getName());
        String parentSignature = getCurrentMethodSignature();

        // 记录方法对象映射
        if (!METHOD_SIGNATURE_TO_METHOD.containsKey(requestId)) {
            METHOD_SIGNATURE_TO_METHOD.put(requestId, method);
            METHOD_TO_SIGNATURE.put(method, methodSignature);
        }

        // 更新调用计数器
        int invocationIndex = INVOCATION_COUNTER.get()
                .compute(methodSignature, (k, v) -> v == null ? 1 : v + 1);

        MethodInvocation invocation = new MethodInvocation(
                methodSignature,
                parentSignature,
                System.currentTimeMillis(),
                method,
                invocationIndex
        );

        CALL_STACK.get().push(invocation);

        REQUEST_METHODS.get()
                .computeIfAbsent(methodSignature, k -> new ArrayList<>())
                .add(invocation);

        // 创建树节点
        MethodNode node = new MethodNode(
                methodSignature,
                0, // 耗时暂为0
                System.currentTimeMillis(),
                0, // 结束时间暂为0
                method,
                invocationIndex
        );

        // 构建树结构
        if (CALL_TREE_ROOT.get() == null) {
            // 根节点
            CALL_TREE_ROOT.set(node);
        }
        else if (CURRENT_NODE.get() != null) {
            CURRENT_NODE.get().addChild(node);
        }

        CURRENT_NODE.set(node);
    }

    /**
     * 记录方法结束
     */
    public static void endMethod() {

        Stack<MethodInvocation> stack = CALL_STACK.get();
        if (!stack.isEmpty()) {
            MethodInvocation invocation = stack.pop();
            invocation.setEndTime(System.currentTimeMillis());

            // 更新当前节点的耗时信息
            if (CURRENT_NODE.get() != null) {
                CURRENT_NODE.get().setEndTime(invocation.getEndTime());
                CURRENT_NODE.get().setDuration(invocation.getDuration());

                // 回退到父节点
                CURRENT_NODE.set(CURRENT_NODE.get().getParent());
            }
        }
    }

    /**
     * 生成方法签名
     * @return 签名
     */
    private static String getCurrentMethodSignature() {
        Stack<MethodInvocation> stack = CALL_STACK.get();
        return stack.isEmpty() ? null : stack.peek().getMethodSignature();
    }

    /**
     * 根据方法签名获取Method对象
     * @param methodSignature 方法签名
     * @return 方法
     */
    public static Method getMethodBySignature(String methodSignature) {
        return METHOD_SIGNATURE_TO_METHOD.get(methodSignature);
    }

    /**
     * 根据Method对象获取方法签名
     * @param method 方法
     * @return 方法签名
     */
    public static String getSignatureByMethod(Method method) {
        return METHOD_TO_SIGNATURE.get(method);
    }

    /**
     * 获取所有已记录的方法映射
     *
     * @return 方法映射
     */
    public static Map<String, Method> getAllMethodMappings() {
        return new HashMap<>(METHOD_SIGNATURE_TO_METHOD);
    }

    /**
     * 清空方法映射（谨慎使用）
     */
    public static void clearMethodMappings() {
        METHOD_SIGNATURE_TO_METHOD.clear();
        METHOD_TO_SIGNATURE.clear();
    }

    /**
     * 清除请求记录
     */
    public static void clear() {
        REQUEST_METHODS.get().clear();
        CALL_STACK.get().clear();
        CALL_TREE_ROOT.set(null);
        CURRENT_NODE.set(null);
        INVOCATION_COUNTER.get().clear();
        REQUEST_ID.remove();
    }

    /**
     * 方法调用信息
     */
    @Data
    public static class MethodInvocation {
        private final String methodSignature;
        private final String parentSignature;
        private final long startTime;
        private long endTime;
        private final Method method;
        private final int invocationIndex;

        /**
         * 构造方法
         * @param methodSignature 方法前面
         * @param parentSignature 父方法签名
         * @param startTime 开始时间
         * @param method 方法
         * @param invocationIndex 调用位置
         */
        public MethodInvocation(String methodSignature, String parentSignature,
                                long startTime, Method method, int invocationIndex) {
            this.methodSignature = methodSignature;
            this.parentSignature = parentSignature;
            this.startTime = startTime;
            this.method = method;
            this.invocationIndex = invocationIndex;
        }

        /**
         * 获取方法耗时
         * @return 方法耗时
         */
        public long getDuration() {
            return endTime - startTime;
        }
    }

    /**
     * 方法节点
     */
    @Data
    public static class MethodNode {
        private String signature;
        private long duration;
        private long startTime;
        private long endTime;
        private Method method;
        private int invocationIndex;
        private MethodNode parent;
        private List<MethodNode> children = new ArrayList<>();

        /**
         * 构造方法
         * @param signature 签名
         * @param duration 耗时
         * @param startTime 开始时间
         * @param endTime 结束时间
         * @param method 方法
         * @param invocationIndex 调用位置
         */
        public MethodNode(String signature, long duration, long startTime,
                          long endTime, Method method, int invocationIndex) {
            this.signature = signature;
            this.duration = duration;
            this.startTime = startTime;
            this.endTime = endTime;
            this.method = method;
            this.invocationIndex = invocationIndex;
        }

        /**
         * 添加子节点
         * @param child 子节点
         */
        public void addChild(MethodNode child) {
            child.setParent(this);
            children.add(child);
        }

        /**
         * 获取总耗时（包含子节点）
         * @return 耗时
         */
        public long getTotalDuration() {
            return duration + children.stream()
                    .mapToLong(MethodNode::getTotalDuration)
                    .sum();
        }

        /**
         * 获取调用路径
         * @return 调用路径
         */
        public String getCallPath() {
            return signature + "#" + invocationIndex +
                    (children.isEmpty() ? "" : " -> " +
                            children.stream()
                                    .map(MethodNode::getCallPath)
                                    .collect(Collectors.joining(" | ")));
        }

        /**
         * 获取带参数的签名显示
         * @return 带参数的签名显示
         */
        public String getSignatureWithParams() {
            return ArgusRequestContext.getSignatureWithParams(this);
        }
    }

    /**
     * 构建树形字符串表示
     * @param node 节点
     * @param depth 深度
     * @return 树形字符串表示
     */
    private static String buildTreeString(MethodNode node, int depth) {
        StringBuilder sb = new StringBuilder();

        // 构建缩进
        StringBuilder indentBuilder = new StringBuilder();
        for (int i = 0; i < depth; i++) {
            indentBuilder.append("  ");
        }
        String indent = indentBuilder.toString();

        // 构建连接线
        String connector;
        if (depth == 0) {
            connector = "";
        } else if (depth == 1) {
            connector = "├── ";
        } else {
            StringBuilder connectorBuilder = new StringBuilder();
            for (int i = 0; i < depth - 1; i++) {
                connectorBuilder.append("│   ");
            }
            connectorBuilder.append("├── ");
            connector = connectorBuilder.toString();
        }

        sb.append(indent)
                .append(connector)
                .append(getSignatureWithParams(node))
                .append("#")
                .append(node.getInvocationIndex())
                .append(" [")
                .append(node.getDuration())
                .append("ms]")
                .append("\n");

        for (MethodNode child : node.getChildren()) {
            sb.append(buildTreeString(child, depth + 1));
        }

        return sb.toString();
    }

    /**
     * 将调用树转换为MethodCallInfo集合
     *
     * 调用信息集合
     * @return 调用信息集合
     */
    public static Set<MethodCallInfo> convertTreeToCallInfos() {
        MethodNode root = CALL_TREE_ROOT.get();
        if (root == null) {
            return Collections.emptySet();
        }

        Set<MethodCallInfo> callInfos = new HashSet<>();
        convertNodeToCallInfo(root, null, callInfos, 0);
        return callInfos;
    }

    /**
     * 转换节点为调用信息
     * @param node 节点
     * @param parent 父节点
     * @param callInfos 调用信息
     * @param depth 深度
     */
    private static void convertNodeToCallInfo(MethodNode node, MethodNode parent,
                                              Set<MethodCallInfo> callInfos, int depth) {
        // 解析签名获取类名和方法名
        String[] parts = node.getSignature().split("#");
        String className = parts[0];
        String methodName = parts[1];

        String parentClassName = parent != null ? parent.getSignature().split("#")[0] : null;
        String parentMethodName = parent != null ? parent.getSignature().split("#")[1] : null;

        MethodCallInfo callInfo = new MethodCallInfo(
                parentClassName, parentMethodName,
                className, methodName,
                "", false, className, className,
                node.getInvocationIndex(), depth
        );

        callInfos.add(callInfo);

        for (MethodNode child : node.getChildren()) {
            convertNodeToCallInfo(child, node, callInfos, depth + 1);
        }
    }
}