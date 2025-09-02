package githubcew.arguslog.monitor.trace;

import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.monitor.trace.asm.MethodCallInfo;
import lombok.Data;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Trace 上下文
 *
 * @author chenenwei
 */
public class ArgusTraceRequestContext {

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

    public static Method getStartMethod() {
        return METHOD_SIGNATURE_TO_METHOD.get(REQUEST_ID.get());

    }

    /**
     * 获取调用树根节点
     */
    public static MethodNode getCallTree() {
        return CALL_TREE_ROOT.get();
    }

    /**
     * 获取格式化的调用树字符串（匹配您要求的格式）
     */
    public static String getFormattedTree() {
        MethodNode root = CALL_TREE_ROOT.get();
        if (root == null) {
            return "No call tree available";
        }
        Map<String, Integer> methodCounts = new HashMap<>();
        return buildTreeString(root, 0, new ArrayList<>(), methodCounts);
    }

    /**
     * 递归构建树形字符串（匹配指定格式）
     */
    private static String buildTreeString(MethodNode node, int depth, List<Boolean> parentIsLastList,  Map<String, Integer> methodCount) {
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

        sb.append(signatureWithParams)
                .append(" [")
                .append(node.getDuration())
                .append("ms]")
                .append("\n");

        // 递归处理子节点
        List<MethodNode> children = node.getChildren();
        for (int i = 0; i < children.size(); i++) {
            boolean childIsLast = (i == children.size() - 1);
            List<Boolean> newParentIsLastList = new ArrayList<>(parentIsLastList);
            newParentIsLastList.add(childIsLast);
            // 显示超过5次则不显示
            String key = children.get(i).getSignature() + depth;
            methodCount.putIfAbsent(key, 0);
            Integer printCount = methodCount.get(key);
            if (Objects.isNull(printCount) || printCount > 3) {
                continue;
            }
            else {
                methodCount.put(key, printCount + 1);
            }
            sb.append(buildTreeString(children.get(i), depth + 1, newParentIsLastList, methodCount));
        }

        return sb.toString();
    }

    /**
     * 获取带参数的简化方法签名（类名.方法名(参数类型)）
     * 如果参数超过64个字符，则截取并在后面添加"..."
     */
    private static String getSignatureWithParams(MethodNode node) {
        String className = getSimpleClassName(node.getSignature());
        String methodName = getMethodName(node.getSignature());

        // 构建参数列表字符串
        String paramsString = buildParamsString(node);

        // 检查总长度是否超过限制
        String fullSignature = className + "." + methodName + "(" + paramsString + ")";

        if (fullSignature.length() > 64) {
            return truncateSignature(className, methodName, paramsString);
        }

        return fullSignature;
    }

    /**
     * 构建参数列表字符串
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

    /**
     * 获取树统计信息（匹配新格式）
     */
    public static String getTreeStatistics() {
        MethodNode root = CALL_TREE_ROOT.get();
        if (root == null) {
            return "";
        }

        return "Argus Trace => \n" +
                "Root: " + getSignatureWithParams(root) + "\n" +
                "Tree:\n" + getFormattedTree();
    }

    public static void startMethod(Method method) {
        if (Objects.isNull(method)) {
            return;
        }

        String requestId = REQUEST_ID.get();
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
        if (CURRENT_NODE.get() == null) {
            // 根节点
            CALL_TREE_ROOT.set(node);
        } else {
            // 添加到当前节点的子节点
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

    private static String getCurrentMethodSignature() {
        Stack<MethodInvocation> stack = CALL_STACK.get();
        return stack.isEmpty() ? null : stack.peek().getMethodSignature();
    }

    /**
     * 根据方法签名获取Method对象
     */
    public static Method getMethodBySignature(String methodSignature) {
        return METHOD_SIGNATURE_TO_METHOD.get(methodSignature);
    }

    /**
     * 根据Method对象获取方法签名
     */
    public static String getSignatureByMethod(Method method) {
        return METHOD_TO_SIGNATURE.get(method);
    }

    /**
     * 获取所有已记录的方法映射
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

    @Data
    public static class MethodInvocation {
        private final String methodSignature;
        private final String parentSignature;
        private final long startTime;
        private long endTime;
        private final Method method;
        private final int invocationIndex;

        public MethodInvocation(String methodSignature, String parentSignature,
                                long startTime, Method method, int invocationIndex) {
            this.methodSignature = methodSignature;
            this.parentSignature = parentSignature;
            this.startTime = startTime;
            this.method = method;
            this.invocationIndex = invocationIndex;
        }

        public long getDuration() {
            return endTime - startTime;
        }
    }

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
         */
        public void addChild(MethodNode child) {
            child.setParent(this);
            children.add(child);
        }

        /**
         * 获取总耗时（包含子节点）
         */
        public long getTotalDuration() {
            return duration + children.stream()
                    .mapToLong(MethodNode::getTotalDuration)
                    .sum();
        }

        /**
         * 获取调用路径
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
         */
        public String getSignatureWithParams() {
            return ArgusTraceRequestContext.getSignatureWithParams(this);
        }
    }

    /**
     * 构建树形字符串表示
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
     * 将调用树转换为MethodCallInfo集合（用于兼容现有代码）
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