package githubcew.arguslog.core.cmd;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 反引号参数合并器
 *
 * <div>用于处理命令行参数中的反引号包围的参数，将多个分散的参数字符串合并为完整的参数</div>
 * <div>主要功能：</div>
 * <div>1. 识别以反引号开始和结束的参数序列</div>
 * <div>2. 将分散在多行的复杂参数（如JSON、XML等）合并为单个参数</div>
 * <div>3. 支持转义反引号的处理</div>
 * <div>4. 提供容错机制处理未闭合的反引号</div>
 */
public class BacktickParameterMerger {

    /**
     * 将反引号包裹的参数整体合并为一个字符串
     *
     * <div>支持复杂 JSON 结构、嵌套 {}、[]、空格等内容的合并</div>
     * <div>处理逻辑：</div>
     * <div>1. 遍历所有参数字符串，识别反引号的开始和结束</div>
     * <div>2. 将反引号内部的多个字符串合并为一个完整字符串</div>
     * <div>3. 保持普通参数的原有顺序</div>
     * <div>4. 处理未闭合反引号的容错情况</div>
     *
     * <div>使用示例：</div>
     * <div>输入：["`{\"a\":1,", "\"b\":{\"x\":2}}`", "true"]</div>
     * <div>输出：["{\"a\":1, \"b\":{\"x\":2}}", "true"]</div>
     *
     * <div>输入：["--data", "`{", "  \"name\": \"test\",", "  \"values\": [1, 2, 3]", "}`", "--flag"]</div>
     * <div>输出：["--data", "{ \"name\": \"test\", \"values\": [1, 2, 3] }", "--flag"]</div>
     *
     * <div>输入：["`abc`", "` abc `", "normal"]</div>
     * <div>输出：["abc", "abc", "normal"]</div>
     *
     * @param tokens 输入的命令参数列表，可能包含被反引号分割的多个字符串
     * @return 合并后的参数列表，反引号包围的内容已被合并为单个参数
     */
    public static List<String> mergeBacktickParams(List<String> tokens) {
        // 检查输入参数是否为空
        if (tokens == null || tokens.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> merged = new ArrayList<>();
        StringBuilder current = null;
        boolean inBacktick = false;

        // 遍历所有参数字符串
        for (String token : tokens) {
            if (!inBacktick) {
                // 当前不在反引号内部，检查是否开始反引号参数
                if (token.startsWith("`")) {
                    // 标记进入反引号模式
                    inBacktick = true;
                    current = new StringBuilder();

                    // 检查反引号是否在同一字符串中开始和结束
                    if (token.endsWith("`") && token.length() > 1) {
                        // 完整的一个反引号参数：`content`
                        String content = extractContent(token);
                        merged.add(content);
                        // 重置状态
                        inBacktick = false;
                        current = null;
                    } else {
                        // 多字符串反引号参数开始：`content...
                        // 去掉开头的反引号，保留内容部分
                        String content = token.substring(1);
                        current.append(content);
                    }
                } else {
                    // 普通参数，直接添加到结果列表
                    merged.add(token);
                }
            } else {
                // 当前处于反引号内部，继续收集内容
                // 检查是否遇到结束反引号
                if (token.endsWith("`")) {
                    // 反引号结束：...content`
                    // 去掉结尾的反引号，保留内容部分
                    String content = token.substring(0, token.length() - 1);
                    // 添加空格分隔符并追加内容
                    current.append(" ").append(content);

                    // 将完整内容添加到结果中，并去除首尾空格
                    String finalContent = current.toString().trim();
                    merged.add(finalContent);

                    // 重置状态，退出反引号模式
                    inBacktick = false;
                    current = null;
                } else {
                    // 反引号中间部分：...content...
                    // 添加空格分隔符并继续收集内容
                    current.append(" ").append(token);
                }
            }
        }

        // 容错处理：如果反引号未闭合，将其作为普通字符串处理
        if (inBacktick && current != null) {
            // 去掉开头的反引号和首尾空格，作为普通参数处理
            String unclosedContent = current.toString().trim();
            merged.add(unclosedContent);
        }

        return merged;
    }

    /**
     * 提取单个字符串中的反引号内容
     *
     * <div>处理完整的反引号参数字符串，去除首尾的反引号和空格</div>
     * <div>使用示例：</div>
     * <div>"`abc`" -> "abc"</div>
     * <div>"` abc `" -> "abc"</div>
     * <div>"`  hello world  `" -> "hello world"</div>
     *
     * @param token 包含反引号的完整字符串字符串
     * @return 提取并清理后的内容字符串
     */
    private static String extractContent(String token) {
        // 去掉开头和结尾的反引号字符
        String content = token.substring(1, token.length() - 1);
        // 去除首尾空格，返回清理后的内容
        return content.trim();
    }

    /**
     * 处理转义的反引号
     *
     * <div>将字符串中的转义反引号序列 "\`" 转换为普通反引号字符 "</div>
     * <div>用于在合并后恢复原始的反引号字符</div>
     *
     * @param str 包含转义反引号的输入字符串
     * @return 处理后的字符串，转义序列已被替换为实际字符
     */
    private static String unescapeBackticks(String str) {
        return str.replace("\\`", "`");
    }

    /**
     * 转义字符串中的反引号
     *
     * <div>将字符串中的普通反引号字符 "`" 转换为转义序列 "\`"</div>
     * <div>用于在需要时保护反引号字符不被解析器识别</div>
     *
     * @param str 包含普通反引号的输入字符串
     * @return 处理后的字符串，反引号已被转义
     */
    public static String escapeBackticks(String str) {
        return str.replace("`", "\\`");
    }
}