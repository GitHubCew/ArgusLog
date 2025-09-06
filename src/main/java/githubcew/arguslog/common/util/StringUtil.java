package githubcew.arguslog.common.util;

import lombok.Data;

import java.util.*;
import java.util.regex.*;

/**
 * 字符串工具
 *
 * @author chenenwei
 */
@Data
public class StringUtil {

    /**
     * 替换#{}正则
     */
    private static final Pattern PATTERN = Pattern.compile("#\\{(.*?)\\}");

    /**
     * 提取结果，包含值和位置信息
     */
    @Data
    public static class ExtractionResult {
        // 提取的值列表
        private final List<String> values;
        // 每个占位符的开始位置
        private final List<Integer> startPositions;
        // 每个占位符的结束位置
        private final List<Integer> endPositions;
        // 字符串模板
        private final String template;


        /**
         * 构造方法
         * @param values 值
         * @param startPositions 开始位置
         * @param endPositions 结束位置
         * @param template 字符串模板
         */
        public ExtractionResult(List<String> values, List<Integer> startPositions,
                                List<Integer> endPositions, String template) {
            this.values = Collections.unmodifiableList(values);
            this.startPositions = Collections.unmodifiableList(startPositions);
            this.endPositions = Collections.unmodifiableList(endPositions);
            this.template = template;
        }

        public int getCount() { return values.size(); }
    }

    /**
     * 提取所有占位符的值和位置信息
     * @param template 模板
     * @return 提取结果
     */
    public static ExtractionResult extractWithPositions(String template) {
        if (template == null) {
            return new ExtractionResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), template);
        }

        List<String> values = new ArrayList<>();
        List<Integer> startPositions = new ArrayList<>();
        List<Integer> endPositions = new ArrayList<>();

        Matcher matcher = PATTERN.matcher(template);
        while (matcher.find()) {
            values.add(matcher.group(1));
            startPositions.add(matcher.start());
            endPositions.add(matcher.end());
        }

        return new ExtractionResult(values, startPositions, endPositions, template);
    }

    /**
     * 仅提取值列表
     * @param template 模板
     * @return 提取结果
     */
    public static List<String> extractValues(String template) {
        ExtractionResult result = extractWithPositions(template);
        return result.getValues();
    }

    /**
     * 按原位置替换回去
     * @param extractionResult 提取结果
     * @param newValues 新值列表
     * @return 替换后的字符串
     */
    public static String replaceBack(ExtractionResult extractionResult, List<String> newValues) {
        return replaceBack(extractionResult, newValues, false);
    }

    /**
     * 按原位置替换回去
     * @param extractionResult 提取结果
     * @param newValues 新值列表
     * @param strict 是否严格模式
     * @return 替换后的字符串
     */
    public static String replaceBack(ExtractionResult extractionResult, List<String> newValues, boolean strict) {
        if (extractionResult == null || extractionResult.getCount() == 0) {
            return extractionResult != null ? extractionResult.getTemplate() : null;
        }

        if (strict && newValues.size() != extractionResult.getCount()) {
            throw new IllegalArgumentException(
                    "替换值数量不匹配: 需要 " + extractionResult.getCount() + " 个，但提供了 " + newValues.size() + " 个");
        }

        String template = extractionResult.getTemplate();
        List<Integer> startPositions = extractionResult.getStartPositions();
        List<Integer> endPositions = extractionResult.getEndPositions();

        StringBuilder result = new StringBuilder();
        int lastIndex = 0;

        for (int i = 0; i < extractionResult.getCount(); i++) {
            int start = startPositions.get(i);
            int end = endPositions.get(i);

            // 添加当前位置之前的内容
            result.append(template, lastIndex, start);

            // 添加替换值（如果提供了的话）
            if (i < newValues.size() && newValues.get(i) != null) {
                result.append(newValues.get(i));
            } else {
                // 没提供替换值，保留原占位符
                result.append(template, start, end);
            }

            lastIndex = end;
        }

        // 添加剩余内容
        if (lastIndex < template.length()) {
            result.append(template.substring(lastIndex));
        }

        return result.toString();
    }

    /**
     * 替换部分值（按索引）
     * @param extractionResult 提取结果
     * @param indexReplacements 索引替换
     * @return 替换后的字符串
     */
    public static String replacePartial(ExtractionResult extractionResult,
                                        Map<Integer, String> indexReplacements) {
        if (extractionResult == null || extractionResult.getCount() == 0) {
            return extractionResult != null ? extractionResult.getTemplate() : null;
        }

        // 创建新的值列表，先复制原值
        List<String> newValues = new ArrayList<>(extractionResult.getValues());

        // 替换指定索引的值
        for (Map.Entry<Integer, String> entry : indexReplacements.entrySet()) {
            int index = entry.getKey();
            if (index >= 0 && index < newValues.size()) {
                newValues.set(index, entry.getValue());
            }
        }

        return replaceBack(extractionResult, newValues);
    }

    /**
     * 一键完成提取、处理、替换
     * @param template 模板
     * @param processor 处理函数
     * @return 替换后的字符串
     */
    public static String processAndReplace(String template,
                                           java.util.function.Function<List<String>, List<String>> processor) {
        ExtractionResult extractionResult = extractWithPositions(template);
        List<String> processedValues = processor.apply(extractionResult.getValues());
        return replaceBack(extractionResult, processedValues);
    }
}
