package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.anno.ArgusProperty;
import githubcew.arguslog.core.cmd.BaseCommand;
import picocli.CommandLine;

import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 显示命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "show",
        description = "显示系统信息",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class ShowCmd extends BaseCommand {

    @CommandLine.Parameters(
            index = "0",
            description = "变量名",
            arity = "0..1",
            defaultValue = "config",
            paramLabel = "variable"
    )
    private String variable;


    @Override
    protected Integer execute() throws Exception {

        // 系统配置
        if (variable.equals("config")) {
            picocliOutput.out(String.join("\n", getConfig()));
        }
        else {
            picocliOutput.error("Variable not found! available：\n" + Arrays.asList("config"));
            return ERROR_CODE;
        }
        return OK_CODE;
    }

    /**
     * 获取配置信息并以格式化表格形式返回
     * 表格包含四列：属性(property)、值(value)、可修改(modifiable)、属性描述(description)
     * 只显示 {@link ArgusProperty#displayInShow()} 为 true 的字段
     * 属性列的宽度根据字段名的最大长度动态调整
     *
     * @return 格式化后的配置信息列表，每行代表表格的一行
     */
    private List<String> getConfig() {
        ArgusProperties argusProperties = ContextUtil.getBean(ArgusProperties.class);
//        ArgusProperties argusProperties = new ArgusProperties();
//        argusProperties.setTraceIncludePackages(new HashSet<>(Arrays.asList("com.example1", "com.example2", "com.example3")));
        List<String> lines = new ArrayList<>();

        final int TOTAL_WIDTH = 100;
        final int VALUE_WIDTH = 30;
        final int MODIFIABLE_WIDTH = 12;
        final int MIN_PROPERTY_WIDTH = 15;
        final int MAX_PROPERTY_WIDTH = 30;

        // 获取所有需要显示的字段
        List<Field> displayFields = Arrays.stream(argusProperties.getClass().getDeclaredFields())
                .filter(field -> !field.getName().startsWith("$"))
                .filter(this::shouldDisplayInShow)
                .collect(Collectors.toList());

        if (displayFields.isEmpty()) {
            return Collections.singletonList("No config properties to display");
        }

        // 动态计算属性列宽度
        int maxPropertyLength = displayFields.stream()
                .map(Field::getName)
                .mapToInt(String::length)
                .max()
                .orElse(MIN_PROPERTY_WIDTH);

        final int PROPERTY_WIDTH = Math.max(MIN_PROPERTY_WIDTH,
                Math.min(maxPropertyLength + 2, MAX_PROPERTY_WIDTH));
        final int DESC_WIDTH = TOTAL_WIDTH - PROPERTY_WIDTH - VALUE_WIDTH - MODIFIABLE_WIDTH - 3;

        // 表头
        String header = padRight("属性", PROPERTY_WIDTH) +
                padRight("值", VALUE_WIDTH)+
                padRight("可修改", MODIFIABLE_WIDTH)+
                padRight("描述", DESC_WIDTH);
        lines.add(header);

        // 标题和属性之间的横向分隔线
        String separator = repeat("─", PROPERTY_WIDTH) +
                repeat("─", VALUE_WIDTH)+
                repeat("─", MODIFIABLE_WIDTH)+
                repeat("─", DESC_WIDTH);
        lines.add(separator);

        // 处理字段
        displayFields.forEach(field -> {
            List<String> fieldLines = formatFieldLineWithWrap(field, argusProperties,
                    PROPERTY_WIDTH, VALUE_WIDTH, MODIFIABLE_WIDTH, DESC_WIDTH);
            lines.addAll(fieldLines);

        });

        return lines;
    }


    /**
     * 将字段信息格式化为支持换行的多行文本
     * 处理长文本的自动换行，并保持表格格式对齐
     * 使用空格作为列分隔符
     *
     * @param field 要格式化的字段对象
     * @param instance 包含字段值的对象实例
     * @param propertyWidth 属性列的宽度
     * @param valueWidth 值列的宽度
     * @param modifiableWidth 可修改列的宽度
     * @param descWidth 描述列的宽度
     * @return 格式化后的多行文本列表
     */
    private List<String> formatFieldLineWithWrap(Field field, Object instance,
                                                 int propertyWidth, int valueWidth,
                                                 int modifiableWidth, int descWidth) {
        List<String> resultLines = new ArrayList<>();
        field.setAccessible(true);

        try {
            String property = field.getName();
            Object value = field.get(instance);
            String valueStr = Objects.toString(value, "null");
            String description = getFieldDescription(field);
            String modifiable = isModifiable(field);

            // 对值和描述进行换行处理
            List<String> wrappedValueLines = wrapText(valueStr, valueWidth);
            List<String> wrappedDescLines = wrapText(description, descWidth);

            // 确定需要多少行
            int maxLines = Math.max(wrappedValueLines.size(), wrappedDescLines.size());

            for (int i = 0; i < maxLines; i++) {
                StringBuilder lineBuilder = new StringBuilder();

                // 第一列：属性名（只在第一行显示）
                if (i == 0) {
                    lineBuilder.append(padRight(property, propertyWidth));
                } else {
                    lineBuilder.append(padRight("", propertyWidth));
                }
                lineBuilder.append(" ");

                // 第二列：值
                String valueLine = i < wrappedValueLines.size() ? wrappedValueLines.get(i) : padRight("", valueWidth);
                lineBuilder.append(valueLine);
                lineBuilder.append(" ");

                // 第三列：可修改（只在第一行显示）
                if (i == 0) {
                    lineBuilder.append(padRight(modifiable, modifiableWidth));
                } else {
                    lineBuilder.append(padRight("", modifiableWidth));
                }
                lineBuilder.append(" ");

                // 第四列：描述
                String descLine = i < wrappedDescLines.size() ? wrappedDescLines.get(i) : padRight("", descWidth);
                lineBuilder.append(descLine);

                resultLines.add(lineBuilder.toString());
            }

        } catch (IllegalAccessException e) {
            // 错误处理
            String errorDesc = "字段访问错误: " + e.getMessage();
            List<String> wrappedErrorDesc = wrapText(errorDesc, descWidth);

            String errorLine = padRight(field.getName(), propertyWidth) + " " +
                    padRight("错误", valueWidth) + " " +
                    padRight("否", modifiableWidth) + " " +
                    wrappedErrorDesc.get(0);
            resultLines.add(errorLine);

            // 添加额外的错误描述行
            for (int i = 1; i < wrappedErrorDesc.size(); i++) {
                String extraLine = padRight("", propertyWidth) + " " +
                        padRight("", valueWidth) + " " +
                        padRight("", modifiableWidth) + " " +
                        wrappedErrorDesc.get(i);
                resultLines.add(extraLine);
            }
        }

        return resultLines;
    }

    /**
     * 检查字段是否应该在show命令中显示
     * 根据 {@link ArgusProperty#displayInShow()} 注解属性进行判断
     *
     * @param field 要检查的字段对象
     * @return 如果字段应该显示返回 true，否则返回 false
     */
    private boolean shouldDisplayInShow(Field field) {
        try {
            ArgusProperty annotation = field.getAnnotation(ArgusProperty.class);
            return annotation != null && annotation.displayInShow();
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查字段是否可以在运行时修改
     * 根据 {@link ArgusProperty#modifyInRunning()} 注解属性进行判断
     *
     * @param field 要检查的字段对象
     * @return 如果字段可以在运行时修改返回 "是"，否则返回 "否"
     */
    private String isModifiable(Field field) {
        try {
            ArgusProperty annotation = field.getAnnotation(ArgusProperty.class);
            if (annotation != null) {
                return annotation.modifyInRunning() ? "yes" : "no";
            }
            return "no"; // 如果没有注解，默认不可修改
        } catch (Exception e) {
            return "no";
        }
    }

    /**
     * 获取字段的描述信息
     * 从 {@link ArgusProperty#description()} 注解属性获取描述信息
     * 如果未设置描述信息，则根据字段名生成默认描述
     *
     * @param field 要获取描述的字段对象
     * @return 字段的描述信息，不会返回 null
     */
    private String getFieldDescription(Field field) {
        try {
            ArgusProperty annotation = field.getAnnotation(ArgusProperty.class);
            if (annotation != null) {
                String description = annotation.description();
                if (description != null && !description.trim().isEmpty()) {
                    return description.trim();
                }
            }
            return generateDescriptionFromFieldName(field.getName());
        } catch (Exception e) {
            return generateDescriptionFromFieldName(field.getName());
        }
    }

    /**
     * 根据字段名生成默认描述信息
     * 将驼峰命名的字段名转换为可读的中文描述
     * 例如："maxConnections" -> "Max Connections 配置"
     *
     * @param fieldName 字段名称
     * @return 生成的描述信息，如果字段名为空则返回 "无描述"
     */
    private String generateDescriptionFromFieldName(String fieldName) {
        if (fieldName == null || fieldName.isEmpty()) {
            return "无描述";
        }

        return Arrays.stream(fieldName.split("(?=[A-Z])"))
                .map(word -> {
                    if (word.length() > 1) {
                        return word.substring(0, 1).toUpperCase() + word.substring(1).toLowerCase();
                    }
                    return word.toUpperCase();
                })
                .collect(Collectors.joining(" ")) + " 配置";
    }

    /**
     * 文本换行处理
     * 将长文本按指定宽度分割成多行，保持单词完整性
     *
     * @param text 要换行的文本
     * @param maxWidth 每行的最大宽度
     * @return 换行后的文本行列表
     */
    private List<String> wrapText(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            lines.add(padRight("", maxWidth));
            return lines;
        }

        if (text.length() <= maxWidth) {
            lines.add(padRight(text, maxWidth));
            return lines;
        }

        // 智能换行：在单词边界处换行
        String[] words = text.split("\\s+");
        StringBuilder currentLine = new StringBuilder();

        for (String word : words) {
            if (currentLine.length() + word.length() + 1 > maxWidth) {
                if (currentLine.length() > 0) {
                    lines.add(padRight(currentLine.toString(), maxWidth));
                    currentLine = new StringBuilder();
                }
                if (word.length() > maxWidth) {
                    lines.addAll(splitLongWord(word, maxWidth));
                    continue;
                }
            }

            if (currentLine.length() > 0) {
                currentLine.append(" ");
            }
            currentLine.append(word);
        }

        if (currentLine.length() > 0) {
            lines.add(padRight(currentLine.toString(), maxWidth));
        }

        return lines;
    }

    /**
     * 分割超长单词
     * 当单个单词长度超过行宽时，强制分割
     *
     * @param word 要分割的单词
     * @param maxWidth 每行的最大宽度
     * @return 分割后的文本行列表
     */
    private List<String> splitLongWord(String word, int maxWidth) {
        return IntStream.range(0, (word.length() + maxWidth - 1) / maxWidth)
                .mapToObj(i -> {
                    int start = i * maxWidth;
                    int end = Math.min(start + maxWidth, word.length());
                    return padRight(word.substring(start, end), maxWidth);
                })
                .collect(Collectors.toList());
    }

    /**
     * 左对齐文本填充
     * 将文本左对齐并在右侧填充空格到指定宽度
     *
     * @param s 要填充的文本
     * @param width 目标宽度
     * @return 填充后的文本
     */
    private String padRight(String s, int width) {
        if (s == null) {
            s = "null";
        }

        if (s.length() >= width) {
            return s.substring(0, width);
        }

        StringBuilder sb = new StringBuilder(s);
        IntStream.range(0, width - s.length())
                .forEach(i -> sb.append(' '));
        return sb.toString();
    }

    /**
     * 重复字符串
     *
     * @param str 要重复的字符串
     * @param count 重复次数
     * @return 重复后的字符串
     */
    private String repeat(String str, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> str)
                .collect(Collectors.joining());
    }

    public static void main(String[] args) {
        ShowCmd showCmd = new ShowCmd();
        List<String> config = showCmd.getConfig();
        System.out.println((String.join("\n", config)));
    }
}
