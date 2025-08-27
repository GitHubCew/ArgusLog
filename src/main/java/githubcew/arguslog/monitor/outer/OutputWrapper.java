package githubcew.arguslog.monitor.outer;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 输出可copy内容生成 - Builder模式实现
 * @author chenenwei
 */
public class OutputWrapper {

    // 复制开始标识
    public static final String COPY_START = "#copystart#";

    // 复制结束标识
    public static final String COPY_END = "#copyend#";

    // 连接符
    public static final String CONCAT = "#concat#";

    // 换行符
    public static final String LINE_SEPARATOR = "\n";

    private final StringBuilder builder;

    private OutputWrapper() {
        this.builder = new StringBuilder();
    }

    private OutputWrapper(StringBuilder builder) {
        this.builder = builder;
    }

    /**
     * 创建实例
     */
    public static OutputWrapper create() {
        return new OutputWrapper();
    }

    /**
     * 创建实例
     */
    public static OutputWrapper from(StringBuilder builder) {
        return new OutputWrapper(builder);
    }

    /**
     * 包装复制开始信息
     */
    public OutputWrapper startCopy() {
        builder.append(COPY_START);
        return this;
    }

    /**
     * 包装复制结束信息
     */
    public OutputWrapper endCopy() {
        builder.append(COPY_END);
        return this;
    }

    /**
     * 添加连接符
     */
    public OutputWrapper concat() {
        builder.append(CONCAT);
        return this;
    }

    /**
     * 添加换行符
     */
    public OutputWrapper newLine() {
        builder.append(LINE_SEPARATOR);
        return this;
    }

    /**
     * 添加自定义文本内容
     */
    public OutputWrapper append(String text) {
        builder.append(text);
        return this;
    }

    /**
     * 添加多个文本内容，用指定分隔符连接
     */
    public OutputWrapper appendAll(List<String> textList, String delimiter) {
        if (Objects.isNull(textList) || textList.isEmpty()) {
            return this;
        }
        String joined = String.join(delimiter, textList);
        builder.append(joined);
        return this;
    }

    /**
     * 包装单个文本为可复制格式
     */
    public OutputWrapper appendCopy(String text) {
        return startCopy().append(text).endCopy();
    }

    /**
     * 包装多个文本为可复制格式，用指定分隔符连接
     */
    public OutputWrapper appendAllCopy(List<String> textList, String delimiter) {
        if (Objects.isNull(textList) || textList.isEmpty()) {
            return this;
        }

        List<String> wrappedList = textList.stream()
                .map(item -> COPY_START + item + COPY_END)
                .collect(Collectors.toList());

        return appendAll(wrappedList, delimiter);
    }

    /**
     * 获取构建结果
     */
    public String build() {
        return builder.toString();
    }

    /**
     * 清空当前构建内容
     */
    public OutputWrapper clear() {
        builder.setLength(0);
        return this;
    }

    /**
     * 获取Builder
     */
    public StringBuilder getBuilder() {
        return builder;
    }

    /**
     * 包装文本复制标签
     * @param text 文本
     * @return 结果
     */
    public static String wrapperCopy(String text) {
        return new OutputWrapper().appendCopy(text).build();
    }

    /**
     * 包装文本列表复制标签
     * @param textList 文本列表
     * @param delimiter 分隔符
     * @return 结果
     */
    public static String wrapperCopy(List<String> textList, String delimiter) {
        return new OutputWrapper().appendAllCopy(textList, delimiter).build();
    }
}