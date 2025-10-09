package githubcew.arguslog.monitor.sql;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * SQL 参数格式化工具类，用于将 Java 对象转换为可读的、类 SQL 字面量形式的字符串表示。
 * <p>
 * 该类主要用于日志记录、SQL 调试等场景，将 MyBatis、JDBC 等框架传入的参数对象格式化为接近 SQL 语句中实际值的形式，
 * 便于开发者查看和排查问题。
 * </p>
 * <p>
 * 支持类型包括：基本类型、字符串、日期时间、二进制数据（BLOB）、数组、集合、枚举、UUID 等。
 * 对敏感或大体积数据（如 BLOB）提供安全预览控制。
 * </p>
 *
 * <h2>线程安全性</h2>
 * <p>本类是线程安全的，所有字段均为 final，且无共享可变状态。</p>
 *
 * @author chenenwei
 */
public class SqlParameterFormatter {

    /**
     * 是否显示 BLOB 内容（默认为 {@code false}，仅显示占位符）。
     */
    private final boolean showBlobContent;

    /**
     * BLOB 数据预览的最大字节数（超出部分将截断并添加省略号）。
     */
    private final int maxBlobPreviewLength;

    /**
     * 普通 {@link java.util.Date} 和 {@link java.sql.Date} 以外的日期类型（如 {@link java.util.Date}）的格式化模式。
     */
    private final String dateFormat;

    /**
     * {@link java.sql.Timestamp} 类型的格式化模式。
     */
    private final String timestampFormat;

    /**
     * 使用默认配置创建参数格式化器。
     * <ul>
     *   <li>BLOB 内容不显示</li>
     *   <li>BLOB 预览长度：20 字节</li>
     *   <li>日期格式：{@code yyyy-MM-dd HH:mm:ss}</li>
     *   <li>时间戳格式：{@code yyyy-MM-dd HH:mm:ss.SSS}</li>
     * </ul>
     */
    public SqlParameterFormatter() {
        this(false, 20, "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss.SSS");
    }

    /**
     * 使用自定义配置创建参数格式化器。
     *
     * @param showBlobContent       是否显示 BLOB 的实际内容（若为 {@code false}，则显示占位符）
     * @param maxBlobPreviewLength  BLOB 预览的最大字节数（必须 ≥ 0）
     * @param dateFormat            普通日期的格式化模式（如 {@code "yyyy-MM-dd"})
     * @param timestampFormat       时间戳的格式化模式（如 {@code "yyyy-MM-dd HH:mm:ss.SSS"})
     */
    public SqlParameterFormatter(boolean showBlobContent, int maxBlobPreviewLength,
                                 String dateFormat, String timestampFormat) {
        this.showBlobContent = showBlobContent;
        this.maxBlobPreviewLength = maxBlobPreviewLength >= 0 ? maxBlobPreviewLength : 0;
        this.dateFormat = dateFormat != null ? dateFormat : "yyyy-MM-dd HH:mm:ss";
        this.timestampFormat = timestampFormat != null ? timestampFormat : "yyyy-MM-dd HH:mm:ss.SSS";
    }

    /**
     * 将 Java 对象格式化为类 SQL 字面量的字符串表示。
     * <div>
     * 根据对象类型进行智能格式化：
     * <ul>
     *   <li>{@code null} → {@code "NULL"}</li>
     *   <li>字符串、字符 → 单引号包裹并转义</li>
     *   <li>数字类型 → 直接输出（保留精度，去除无意义尾零）</li>
     *   <li>日期时间 → 按配置格式化并单引号包裹</li>
     *   <li>byte[] → BLOB 表示（可配置是否显示内容）</li>
     *   <li>数组/集合 → 转换为 SQL 元组形式 {@code (val1, val2, ...)}</li>
     *   <li>枚举 → 使用 {@code name()} 并单引号包裹</li>
     *   <li>其他类型 → 尝试解析为数字，否则转为字符串并转义</li>
     * </ul>
     * </div>
     *
     * @param param 待格式化的参数对象
     * @return 格式化后的 SQL 字面量字符串，若为 {@code null} 则返回 {@code "NULL"}
     */
    public String formatParameter(Object param) {
        if (param == null) {
            return "NULL";
        }

        Class<?> paramType = param.getClass();

        // 基本类型和包装类
        if (paramType == String.class || paramType == Character.class || paramType == char.class) {
            return formatStringParam(param);
        }

        if (paramType == Integer.class || paramType == int.class ||
                paramType == Long.class || paramType == long.class ||
                paramType == Short.class || paramType == short.class ||
                paramType == Byte.class || paramType == byte.class) {
            return param.toString();
        }

        if (paramType == Float.class || paramType == float.class ||
                paramType == Double.class || paramType == double.class) {
            return formatFloatParam(param);
        }

        if (paramType == BigDecimal.class) {
            return formatBigDecimal((BigDecimal) param);
        }

        if (paramType == BigInteger.class) {
            return param.toString();
        }

        if (paramType == Boolean.class || paramType == boolean.class) {
            return (Boolean) param ? "1" : "0";
        }

        // 日期时间类型
        if (Date.class.isAssignableFrom(paramType)) {
            return formatDateParam(param, paramType);
        }

        // 二进制数据
        if (paramType == byte[].class) {
            return formatBlobParam((byte[]) param);
        }

        // 数组和集合
        if (paramType.isArray()) {
            return formatArrayParam(param);
        }

        if (Collection.class.isAssignableFrom(paramType)) {
            return formatCollectionParam((Collection<?>) param);
        }

        // 枚举
        if (Enum.class.isAssignableFrom(paramType)) {
            return "'" + ((Enum<?>) param).name() + "'";
        }

        // UUID
        if (paramType == UUID.class) {
            return "'" + param.toString() + "'";
        }

        // 其他类型
        return formatUnknownParam(param);
    }

    /**
     * 格式化字符串或字符类型参数。
     * <p>对单引号、反斜杠等特殊字符进行 SQL 转义，并用单引号包裹。</p>
     *
     * @param param 字符串或字符对象
     * @return 转义后的 SQL 字符串字面量
     */
    private String formatStringParam(Object param) {
        return "'" + escapeSql(param.toString()) + "'";
    }

    /**
     * 格式化浮点数类型参数（Float/Double）。
     * <p>
     * 处理科学计数法（如 1.23E10）并转换为普通十进制表示；
     * 去除小数点后无意义的尾随零（如 10.0 → 10）。
     * </p>
     *
     * @param param 浮点数对象
     * @return 格式化后的数字字符串
     */
    private String formatFloatParam(Object param) {
        String str = param.toString();
        // 处理科学计数法
        if (str.contains("E") || str.contains("e")) {
            BigDecimal decimal = new BigDecimal(str);
            return decimal.stripTrailingZeros().toPlainString();
        }

        // 去除多余的0
        if (str.contains(".")) {
            str = str.replaceAll("0*$", "").replaceAll("\\.$", "");
        }
        return str;
    }

    /**
     * 格式化 {@link BigDecimal} 类型参数。
     * <p>去除尾随零并转换为普通十进制字符串（如 100.00 → 100）。</p>
     *
     * @param decimal BigDecimal 对象
     * @return 格式化后的字符串
     */
    private String formatBigDecimal(BigDecimal decimal) {
        return decimal.stripTrailingZeros().toPlainString();
    }

    /**
     * 格式化日期时间类型参数。
     * <p>根据具体子类型选择不同格式：</p>
     * <ul>
     *   <li>{@link java.sql.Date} → 仅日期（如 2023-01-01）</li>
     *   <li>{@link java.sql.Time} → 仅时间（如 14:30:00）</li>
     *   <li>{@link java.sql.Timestamp} → 使用 {@link #timestampFormat}</li>
     *   <li>其他 {@link Date} 子类 → 使用 {@link #dateFormat}</li>
     * </ul>
     *
     * @param param      日期对象
     * @param paramType  参数的实际类型（用于精确判断）
     * @return 格式化后的日期字符串，用单引号包裹
     */
    private String formatDateParam(Object param, Class<?> paramType) {
        try {
            SimpleDateFormat formatter;
            if (param instanceof java.sql.Date) {
                formatter = new SimpleDateFormat("yyyy-MM-dd");
            } else if (param instanceof java.sql.Time) {
                formatter = new SimpleDateFormat("HH:mm:ss");
            } else if (param instanceof java.sql.Timestamp) {
                formatter = new SimpleDateFormat(timestampFormat);
            } else {
                formatter = new SimpleDateFormat(dateFormat);
            }
            return "'" + formatter.format(param) + "'";
        } catch (Exception e) {
            return "'" + param.toString() + "'";
        }
    }

    /**
     * 格式化二进制数据（BLOB）。
     * <p>
     * 若 {@link #showBlobContent} 为 {@code false}，则返回占位符；
     * 否则以十六进制字符串形式显示（如 {@code x'48656c6c6f'}），并限制预览长度。
     * </p>
     *
     * @param bytes 二进制字节数组
     * @return BLOB 的格式化表示
     */
    private String formatBlobParam(byte[] bytes) {
        if (!showBlobContent) {
            return "<BLOB>(" + bytes.length + " bytes)";
        }

        if (bytes.length <= maxBlobPreviewLength) {
            return "x'" + bytesToHex(bytes) + "'";
        } else {
            return "x'" + bytesToHex(Arrays.copyOf(bytes, maxBlobPreviewLength)) + "...' (" + bytes.length + " bytes)";
        }
    }

    /**
     * 格式化 Java 数组参数。
     * <p>转换为 SQL 元组形式：{@code (val1, val2, ...)}，空数组返回 {@code NULL}。</p>
     *
     * @param array 任意类型的数组对象
     * @return 格式化后的元组字符串
     */
    private String formatArrayParam(Object array) {
        int length = Array.getLength(array);
        if (length == 0) return "NULL";

        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatParameter(Array.get(array, i)));
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * 格式化集合参数（如 List、Set）。
     * <p>转换为 SQL 元组形式：{@code (val1, val2, ...)}，空集合返回 {@code NULL}。</p>
     *
     * @param collection 集合对象
     * @return 格式化后的元组字符串
     */
    private String formatCollectionParam(Collection<?> collection) {
        if (collection.isEmpty()) return "NULL";

        StringBuilder sb = new StringBuilder("(");
        Iterator<?> it = collection.iterator();
        for (int i = 0; it.hasNext(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatParameter(it.next()));
        }
        sb.append(")");
        return sb.toString();
    }

    /**
     * 格式化未知类型的参数。
     * <p>
     * 首先尝试将其 {@code toString()} 结果解析为数字（避免将数字字符串错误加引号）；
     * 若失败，则视为字符串并进行 SQL 转义。
     * </p>
     *
     * @param param 未知类型的对象
     * @return 格式化后的字符串
     */
    private String formatUnknownParam(Object param) {
        String str = param.toString();
        try {
            new BigDecimal(str);
            return str; // 是数字，直接返回
        } catch (NumberFormatException e) {
            return "'" + escapeSql(str) + "'"; // 非数字，视为字符串
        }
    }

    /**
     * 对字符串进行 SQL 转义，防止日志中出现歧义或注入假象。
     * <p>转义字符包括：单引号、反斜杠、双引号、退格、换行、回车、制表符。</p>
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    private String escapeSql(String value) {
        if (value == null) return "";
        return value.replace("'", "''")
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 将字节数组转换为十六进制字符串（小写）。
     *
     * @param bytes 字节数组
     * @return 对应的十六进制字符串（如 {@code "48656c6c6f"} 表示 "Hello"）
     */
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}