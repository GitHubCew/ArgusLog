package githubcew.arguslog.core.cmd;

import picocli.CommandLine;
import picocli.CommandLine.IParameterConsumer;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

import java.util.*;

/**
 * 反引号参数消费者
 *
 * <div>实现 picocli 的 IParameterConsumer 接口，用于处理命令行参数中的反引号参数</div>
 * <div>主要功能包括：</div>
 * <div>1. 根据参数类型和数量要求收集原始参数</div>
 * <div>2. 合并反引号包围的参数（支持多行参数）</div>
 * <div>3. 根据目标类型设置参数值</div>
 *
 * <div>使用场景：当命令行参数包含空格或需要多行输入时，可以用反引号包围参数</div>
 * <div>例如：--message `This is a multi-line</div>
 * <div>parameter with spaces`</div>
 *
 * @author chenenwei
 */
public class BacktickConsumer implements IParameterConsumer {

    /**
     * 消费参数的主要方法
     *
     * <div>该方法被 picocli 框架调用，用于处理命令行参数</div>
     * <div>处理流程：</div>
     * <div>1. 根据参数规格收集原始令牌</div>
     * <div>2. 合并反引号参数</div>
     * <div>3. 根据参数类型设置最终值</div>
     *
     * @param args 参数栈，包含所有待处理的命令行参数
     * @param argSpec 参数规格，描述参数的元数据信息
     * @param commandSpec 命令规格，描述整个命令的元数据信息
     */
    @Override
    public void consumeParameters(Stack<String> args, ArgSpec argSpec, CommandSpec commandSpec) {
        List<String> originalTokens = new ArrayList<>();

        // 根据参数类型决定收集策略
        if (argSpec instanceof OptionSpec) {
            // 对于选项参数（以-或--开头的参数），根据数量要求收集参数
            collectBasedOnArity(args, argSpec, originalTokens);
        } else if (argSpec instanceof PositionalParamSpec) {
            // 对于位置参数（不带有选项标识的参数），根据数量要求收集参数
            collectBasedOnArity(args, argSpec, originalTokens);
        }

        // 合并反引号参数：将反引号包围的多个令牌合并为一个参数
        List<String> mergedTokens = BacktickParameterMerger.mergeBacktickParams(originalTokens);

        // 根据参数类型设置值：处理数组、列表、集合等不同类型
        setValueBasedOnType(argSpec, mergedTokens);
    }

    /**
     * 根据参数数量要求收集参数
     *
     * <div>根据参数的 arity（数量要求）从参数栈中收集相应数量的参数</div>
     * <div>支持三种模式：</div>
     * <div>1. 固定数量参数：arity 为固定值如 1, 3 等</div>
     * <div>2. 可变数量参数：arity 为范围如 0..*, 1..* 等</div>
     * <div>3. 无参数：arity 为 0，通常是标志选项</div>
     *
     * @param args 参数栈，包含所有待处理的命令行参数
     * @param argSpec 参数规格，包含 arity 等信息
     * @param collected 收集到的参数列表
     */
    private void collectBasedOnArity(Stack<String> args, ArgSpec argSpec, List<String> collected) {
        CommandLine.Range arity = argSpec.arity();
        int max = arity.max();
        int min = arity.min();

        if (max == 0) {
            // 无参数情况（通常是标志选项，如 --verbose）
            return;
        }

        if (max < 0) {
            // 可变参数数量（如 0..* 或 1..*），收集所有直到遇到下一个选项
            // 这种模式下会持续收集参数，直到参数栈为空或遇到下一个选项参数
            while (!args.isEmpty() && !isOption(args.peek())) {
                collected.add(args.pop());
            }
        } else {
            // 固定数量参数，收集指定数量的参数
            // 遇到选项参数或参数栈为空时停止收集
            for (int i = 0; i < max && !args.isEmpty() && !isOption(args.peek()); i++) {
                collected.add(args.pop());
            }
        }
    }

    /**
     * 判断字符串是否为选项参数
     *
     * <div>选项参数通常以单个短横线(-)或双短横线(--)开头</div>
     * <div>例如：-f, --file, --help 等</div>
     *
     * @param arg 待检查的参数字符串
     * @return 如果是选项参数返回 true，否则返回 false
     */
    private boolean isOption(String arg) {
        return arg.startsWith("-");
    }

    /**
     * 根据参数类型设置值
     *
     * <div>根据参数的目标类型，将字符串值转换为相应的类型并设置到参数规格中</div>
     * <div>支持单值参数和多值参数的处理</div>
     *
     * @param argSpec 参数规格
     * @param values 参数值列表
     */
    private void setValueBasedOnType(ArgSpec argSpec, List<String> values) {
        if (values.isEmpty()) {
            // 如果没有值，不进行任何设置
            return;
        }

        Class<?> type = argSpec.type();

        if (argSpec.isMultiValue()) {
            // 多值参数处理：数组、List、Set 等
            setMultiValue(argSpec, type, values);
        } else {
            // 单值参数处理
            setSingleValue(argSpec, type, values);
        }
    }

    /**
     * 设置多值参数
     *
     * <div>处理多种类型的多值参数：</div>
     * <div>1. 数组类型：包括基本类型数组和对象数组</div>
     * <div>2. List 类型：保持顺序的列表</div>
     * <div>3. Set 类型：不重复的集合，使用 LinkedHashSet 保持插入顺序</div>
     * <div>4. 其他集合类型：使用 picocli 的默认机制处理</div>
     *
     * @param argSpec 参数规格
     * @param type 参数类型
     * @param values 参数值列表
     */
    private void setMultiValue(ArgSpec argSpec, Class<?> type, List<String> values) {
        if (type.isArray()) {
            // 数组类型：创建相应类型的数组
            Object array = createArray(type.getComponentType(), values);
            argSpec.setValue(array);
        } else if (List.class.isAssignableFrom(type)) {
            // List 类型：直接使用值列表
            argSpec.setValue(values);
        } else if (Set.class.isAssignableFrom(type)) {
            // Set 类型：使用 LinkedHashSet 去重但保持顺序
            argSpec.setValue(new LinkedHashSet<>(values));
        } else {
            // 其他集合类型：使用 picocli 的默认机制逐个设置
            for (String value : values) {
                argSpec.setValue(value);
            }
        }
    }

    /**
     * 设置单值参数
     *
     * <div>对于单值参数，确保只有一个值，然后设置该值</div>
     * <div>如果提供多个值，会抛出 ParameterException</div>
     *
     * @param argSpec 参数规格
     * @param type 参数类型
     * @param values 参数值列表
     * @throws CommandLine.ParameterException 当提供多个值但期望单值时抛出
     */
    private void setSingleValue(ArgSpec argSpec, Class<?> type, List<String> values) {
        if (values.size() > 1) {
            throw new CommandLine.ParameterException(
                    argSpec.command().commandLine(),
                    String.format("Expected single value for %s but got multiple: %s",
                            argSpec.toString(), values)
            );
        }

        String value = values.get(0);

        // 对于单值参数，直接设置字符串值，让 picocli 的类型转换器处理类型转换
        argSpec.setValue(value);
    }

    /**
     * 创建数组
     *
     * <div>根据组件类型创建相应类型的数组，并将字符串值转换为数组元素</div>
     * <div>支持常见的类型：String、Integer/int、Boolean/boolean 等</div>
     * <div>对于不常见的类型，使用反射创建数组</div>
     *
     * @param componentType 数组组件类型
     * @param values 字符串值列表
     * @return 创建并填充好的数组
     */
    private Object createArray(Class<?> componentType, List<String> values) {
        if (componentType == String.class) {
            // 字符串数组
            return values.toArray(new String[0]);
        } else if (componentType == Integer.class || componentType == int.class) {
            if (componentType == int.class) {
                // 基本类型 int 数组
                int[] array = new int[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = Integer.parseInt(values.get(i));
                }
                return array;
            } else {
                // Integer 对象数组
                Integer[] array = new Integer[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = Integer.parseInt(values.get(i));
                }
                return array;
            }
        } else if (componentType == Boolean.class || componentType == boolean.class) {
            if (componentType == boolean.class) {
                // 基本类型 boolean 数组
                boolean[] array = new boolean[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = Boolean.parseBoolean(values.get(i));
                }
                return array;
            } else {
                // Boolean 对象数组
                Boolean[] array = new Boolean[values.size()];
                for (int i = 0; i < values.size(); i++) {
                    array[i] = Boolean.parseBoolean(values.get(i));
                }
                return array;
            }
        } else {
            // 其他类型：使用反射创建数组
            Object array = java.lang.reflect.Array.newInstance(componentType, values.size());
            for (int i = 0; i < values.size(); i++) {
                Object converted = convertValue(componentType, values.get(i));
                java.lang.reflect.Array.set(array, i, converted);
            }
            return array;
        }
    }

    /**
     * 转换值类型
     *
     * <div>将字符串值转换为目标类型</div>
     * <div>支持常见的基本类型和包装类型</div>
     * <div>对于不支持的类型，返回原始字符串值，让 picocli 处理转换</div>
     *
     * @param targetType 目标类型
     * @param value 字符串值
     * @return 转换后的值
     */
    private Object convertValue(Class<?> targetType, String value) {
        // 基本类型转换
        if (targetType == String.class) return value;
        if (targetType == Integer.class || targetType == int.class)
            return Integer.parseInt(value);
        if (targetType == Boolean.class || targetType == boolean.class)
            return Boolean.parseBoolean(value);
        if (targetType == Long.class || targetType == long.class)
            return Long.parseLong(value);
        if (targetType == Double.class || targetType == double.class)
            return Double.parseDouble(value);
        if (targetType == Float.class || targetType == float.class)
            return Float.parseFloat(value);

        // 对于不支持的类型，返回字符串值，让 picocli 的类型转换器处理
        return value;
    }
}