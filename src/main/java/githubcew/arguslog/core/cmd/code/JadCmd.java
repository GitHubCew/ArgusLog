package githubcew.arguslog.core.cmd.code;

import githubcew.arguslog.common.util.DecompilerUtil;
import githubcew.arguslog.common.util.ProxyUtil;
import githubcew.arguslog.common.util.SpringUtil;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.core.cmd.ColorWrapper;
import picocli.CommandLine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * 反编译命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "jad",
        description = "反编译指定类",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class JadCmd extends BaseCommand {

    @CommandLine.Parameters(
            index = "0",
            description = "类全限定名",
            arity = "1",
            paramLabel = "className"
    )
    private String fullClassName;

    @CommandLine.Parameters(
            index = "1",
            description = "方法名列表",
            arity = "0..*",
            paramLabel = "methodNames"
    )
    private List<String> methodNames;

    @CommandLine.Option(
            names = {"-p", "--proxy",},
            description = "显示代理对象字节码",
            arity = "0",
            fallbackValue = "true",
            paramLabel = "isProxy"
    )
    private boolean isProxy;

    @CommandLine.Option(
            names = {"-om", "--only-method"},
            description = "只显示方法声明",
            arity = "0",
            fallbackValue = "true"
    )
    private boolean onlyMethod;

    @Override
    protected Integer execute() throws Exception {
        String decompiledCode = getDecompiledCode();
        String highlight = SyntaxHighlighter.highlight(decompiledCode);
        picocliOutput.out(highlight);
        return OK_CODE;
    }

    /**
     * 根据条件获取反编译代码
     *
     * @return 反编译代码
     */
    private String getDecompiledCode() throws Exception {
        Object bean = SpringUtil.getByFullClassName(fullClassName);

        // 容器管理的 Bean
        if (bean != null) {
            return decompileBean(bean);
        }
        // 非容器管理的类
        else {
            return decompileClassDirectly();
        }
    }

    /**
     * 反编译容器管理的 Bean
     *
     * @param bean 对象
     * @ return 反编译代码
     */
    private String decompileBean(Object bean) {
        boolean isJdkProxy = ProxyUtil.isJdkProxyClass(bean);
        boolean decompileFullClass = Objects.isNull(methodNames) || methodNames.isEmpty();

        // JDK 代理特殊处理
        if (isJdkProxy) {
            // jdk代理类需要通过对象获取运行的字节码
            if (isProxy) {
                return decompileFullClass ? DecompilerUtil.decompileClass(bean, true, onlyMethod)
                        : DecompilerUtil.decompileMethods(bean, true, methodNames);
            }
            else {
                // 直接使用原始类
                return decompileFullClass ? DecompilerUtil.decompileClass(fullClassName, onlyMethod)
                        : DecompilerUtil.decompileMethods(fullClassName, methodNames);
            }
        }
        // 其他代理类型（主要是 CGLIB）
        else {
            if (isProxy) {
                // 代理类
                return decompileFullClass ? DecompilerUtil.decompileClass(bean, true, onlyMethod)
                        : DecompilerUtil.decompileMethods(bean, true, methodNames);
            }
            else {
                // 原始类
                String targetClassName = ProxyUtil.extractOriginalClassName(fullClassName);
                return decompileFullClass ? DecompilerUtil.decompileClass(targetClassName, onlyMethod)
                        : DecompilerUtil.decompileMethods(ProxyUtil.extractOriginalClassName(targetClassName), methodNames);
            }
        }
    }

    /**
     * 直接通过类名反编译
     *
     * @ return 反编译代码
     */
    private String decompileClassDirectly() {
        return (Objects.isNull(methodNames) || methodNames.isEmpty()) ?
                DecompilerUtil.decompileClass(fullClassName, onlyMethod) :
                DecompilerUtil.decompileMethods(fullClassName, methodNames);
    }

    /**
     * Java 语法高亮器（适配 ColorWrapper）
     */
    public static class SyntaxHighlighter {

        // 关键字和修饰符（柔和的蓝色）
        private static final String KEYWORDS =
                "\\b(public|private|protected|static|final|class|interface|enum|extends|implements|import|package|new|return|if|else|for|while|do|switch|case|default|break|continue|try|catch|finally|throw|throws|synchronized|volatile|transient|native|strictfp|assert|boolean|byte|char|short|int|long|float|double|void|true|false|null|this|super)\\b";

        // 注解（柔和的紫色）
        private static final String ANNOTATIONS =
                "(?<!\\w)@[a-zA-Z_][a-zA-Z0-9_]*(?:\\.[a-zA-Z_][a-zA-Z0-9_]*)*";

        // 内部类（柔和的绿色）- 匹配包含$的类名
        private static final String INNER_CLASSES =
                "\\b[A-Z][a-zA-Z0-9_]*\\$[A-Z][a-zA-Z0-9_]*\\b";

        // 用于保护注释和字符串的映射
        private static final Map<String, String> PROTECTED_CONTENT = new HashMap<>();
        private static int protectionId = 0;

        public static String highlight(String code) {
            if (code == null || code.isEmpty()) {
                return code;
            }

            // 清空保护映射
            PROTECTED_CONTENT.clear();
            protectionId = 0;

            // 先保护注释和字符串，防止它们被误匹配
            code = protectCommentsAndStrings(code);

            StringBuilder result = new StringBuilder();
            int lastEnd = 0;

            // 按优先级顺序匹配
            Pattern pattern = Pattern.compile(
                    "(" + ANNOTATIONS + ")|" +      // 组1: 注解名称
                            "(" + INNER_CLASSES + ")|" +    // 组2: 内部类
                            "(" + KEYWORDS + ")",           // 组3: 关键字
                    Pattern.MULTILINE
            );

            Matcher matcher = pattern.matcher(code);

            while (matcher.find()) {
                result.append(code, lastEnd, matcher.start());
                String match = matcher.group();

                if (matcher.group(1) != null) { // 注解名称
                    result.append(ColorWrapper.color(match, "BCB500"));
                } else if (matcher.group(2) != null) { // 内部类
                    result.append(highlightInnerClass(match)); // 特殊处理内部类
                } else if (matcher.group(3) != null) { // 关键字
                    result.append(ColorWrapper.color(match, "D97317"));
                } else {
                    result.append(match);
                }

                lastEnd = matcher.end();
            }

            // 添加剩余文本
            if (lastEnd < code.length()) {
                result.append(code.substring(lastEnd));
            }

            // 最后处理泛型（需要更精确的匹配）
            String processedCode = highlightGenerics(result.toString());

            // 恢复被保护的注释和字符串
            String restoredCode = restoreCommentsAndStrings(processedCode);

            // 高亮方法名
            return highlightMethodNames(restoredCode);
        }

        /**
         * 保护注释和字符串内容，防止被误匹配
         *
         * @param code 待处理的代码
         * @param code 处理后的代码
         */
        private static String protectCommentsAndStrings(String code) {
            StringBuilder result = new StringBuilder();
            Pattern pattern = Pattern.compile(
                    "(//[^\n]*|/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/)|\"([^\"\\\\]|\\\\.)*\"|'([^'\\\\]|\\\\.)'",
                    Pattern.MULTILINE
            );

            Matcher matcher = pattern.matcher(code);
            int lastEnd = 0;

            while (matcher.find()) {
                result.append(code, lastEnd, matcher.start());

                String protectedContent = matcher.group();
                String placeholder = "###PROTECTED_" + (protectionId++) + "###";

                PROTECTED_CONTENT.put(placeholder, protectedContent);
                result.append(placeholder);

                lastEnd = matcher.end();
            }

            if (lastEnd < code.length()) {
                result.append(code.substring(lastEnd));
            }

            return result.toString();
        }

        /**
         * 恢复被保护的注释和字符串内容
         * @param code 待处理的代码
         * @return 处理后的代码
         */
        private static String restoreCommentsAndStrings(String code) {
            for (Map.Entry<String, String> entry : PROTECTED_CONTENT.entrySet()) {
                code = code.replace(entry.getKey(), entry.getValue());
            }
            return code;
        }

        /**
         * 精确匹配和高亮泛型
         *
         * @param code 待处理的代码
         * @return 处理后的代码
         */
        private static String highlightGenerics(String code) {
            StringBuilder result = new StringBuilder();
            int lastEnd = 0;
            int bracketCount = 0;
            int genericStart = -1;

            for (int i = 0; i < code.length(); i++) {
                char c = code.charAt(i);

                if (c == '<') {
                    // 检查是否是泛型的开始
                    if (bracketCount == 0 && isGenericStart(code, i)) {
                        genericStart = i;
                    }
                    bracketCount++;
                } else if (c == '>') {
                    bracketCount--;
                    if (bracketCount == 0 && genericStart != -1) {
                        // 找到泛型结束
                        result.append(code, lastEnd, genericStart);
                        String generic = code.substring(genericStart, i + 1);
                        result.append(ColorWrapper.color(generic, "A6B8C8"));
                        lastEnd = i + 1;
                        genericStart = -1;
                    }
                }
            }

            // 添加剩余文本
            if (lastEnd < code.length()) {
                result.append(code.substring(lastEnd));
            }

            return result.toString();
        }

        /**
         * 判断是否是泛型
         *
         * @param code 待处理的代码
         * @param position 当前位置
         * @return 是否是泛型
         */
        private static boolean isGenericStart(String code, int position) {
            if (position == 0) return false;

            // 检查前面的字符
            char prevChar = code.charAt(position - 1);

            return Character.isLetterOrDigit(prevChar) || prevChar == '_' ||
                    prevChar == ',' || prevChar == '>' || prevChar == ']';
        }

        /**
         * 高亮内部类，对每个部分分别着色
         *
         * @param innerClassName 内部类名称
         * @return 处理后的内部类名称
         */
        private static String highlightInnerClass(String innerClassName) {
            if (!innerClassName.contains("$")) {
                return ColorWrapper.color(innerClassName, "4EC9B0");
            }

            String[] parts = innerClassName.split("\\$");
            StringBuilder result = new StringBuilder();

            for (int i = 0; i < parts.length; i++) {
                if (i > 0) {
                    result.append("$"); // 添加分隔符
                }
                result.append(ColorWrapper.color(parts[i], "4EC9B0"));
            }

            return result.toString();
        }
    }

    /**
     * 高亮方法名（必须在注释/字符串恢复后调用）
     *
     * @param code 待处理的代码
     * @return 处理后的代码
     */
    private static String highlightMethodNames(String code) {
        StringBuilder result = new StringBuilder();
        Pattern pattern = Pattern.compile(
                "(?<=\\.|\\s)([a-zA-Z][a-zA-Z0-9_]*)\\s*(?=\\()",
                Pattern.MULTILINE
        );
        Matcher matcher = pattern.matcher(code);
        int lastEnd = 0;

        while (matcher.find()) {
            String methodName = matcher.group(1);

            // 避免高亮关键字或修饰符（虽然它们后面几乎不可能接(，但安全起见）
            if (isKeywordOrModifier(methodName)) {
                result.append(code, lastEnd, matcher.end());
                lastEnd = matcher.end();
                continue;
            }

            result.append(code, lastEnd, matcher.start());
            result.append(ColorWrapper.color(methodName, "FFC463"));
            lastEnd = matcher.start() + methodName.length();
        }

        if (lastEnd < code.length()) {
            result.append(code.substring(lastEnd));
        }

        return result.toString();
    }

    /**
     * 判断是否为关键字或修饰符
     *
     * @param word 待判断的单词
     * @return 是否为关键字或修饰符
     */
    private static boolean isKeywordOrModifier(String word) {
        String allReserved =
                "public|private|protected|static|final|abstract|synchronized|volatile|transient|native|strictfp|" +
                        "class|interface|enum|extends|implements|import|package|new|return|if|else|for|while|do|switch|" +
                        "case|default|break|continue|try|catch|finally|throw|throws|assert|boolean|byte|char|short|int|" +
                        "long|float|double|void|true|false|null|this|super";
        return java.util.Arrays.asList(allReserved.split("\\|")).contains(word);
    }
}
