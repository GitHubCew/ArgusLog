package githubcew.arguslog.monitor.sql;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 格式化工具类，用于将原始 SQL 语句美化为结构清晰、可读性强的格式。
 * <div>
 * 支持以下特性：
 * <ul>
 *   <li>关键字大写/小写转换</li>
 *   <li>主关键字（如 SELECT、FROM、WHERE）自动换行</li>
 *   <li>子查询自动缩进</li>
 *   <li>函数、字符串、注释的正确识别与保留</li>
 *   <li>逗号后换行（适用于字段列表、值列表等）</li>
 *   <li>可配置缩进大小、缩进字符、行宽等</li>
 * </ul>
 * </div>
 * <p>
 * 该工具通过正则表达式分词（tokenization）识别 SQL 中的关键元素（关键字、函数、字符串、注释、标点等），
 * 并结合状态机（{@link FormatState}）动态控制换行与缩进。
 * </p>
 * <p>
 * <b>注意：</b>本工具主要用于日志展示和调试，不保证对所有 SQL 方言 100% 兼容，但对主流 SQL（MySQL、PostgreSQL、Oracle 等）有良好支持。
 * </p>
 *
 * @author chenenwei
 */
public class SqlFormatter {

    /**
     * SQL 关键字集合（大写形式），用于识别和格式化。
     * <p>包含 DML、DDL、DCL、TCL 及常用子句关键字。</p>
     */
    private static final Set<String> KEYWORDS = new HashSet<>(Arrays.asList(
            "SELECT", "FROM", "WHERE", "AND", "OR", "IN", "NOT", "LIKE", "BETWEEN",
            "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "CREATE", "DROP",
            "TABLE", "INDEX", "VIEW", "SEQUENCE", "TRIGGER", "PROCEDURE", "FUNCTION",
            "ALTER", "ADD", "MODIFY", "COLUMN", "PRIMARY", "KEY", "FOREIGN", "UNIQUE",
            "REFERENCES", "DEFAULT", "NULL", "NOT NULL", "CHECK", "CONSTRAINT",
            "ORDER BY", "GROUP BY", "HAVING", "JOIN", "LEFT", "RIGHT", "INNER", "OUTER",
            "ON", "AS", "DISTINCT", "COUNT", "SUM", "AVG", "MAX", "MIN", "CASE", "WHEN",
            "THEN", "ELSE", "END", "EXISTS", "UNION", "ALL", "ANY", "SOME", "LIMIT",
            "OFFSET", "TOP", "FETCH", "NEXT", "ROW", "ROWS", "ONLY", "WITH", "RECURSIVE",
            "PARTITION BY", "OVER", "ROW_NUMBER", "RANK", "DENSE_RANK", "LEAD", "LAG",
            "FIRST_VALUE", "LAST_VALUE", "NTILE", "PERCENT", "TIES", "ROLLUP", "CUBE",
            "GROUPING SETS", "PIVOT", "UNPIVOT", "MATERIALIZED", "EXPLAIN", "ANALYZE",
            "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "TRUNCATE", "GRANT", "REVOKE",
            "DENY", "EXEC", "EXECUTE", "DECLARE", "CURSOR", "OPEN", "CLOSE", "FETCH",
            "DEALLOCATE", "PREPARE", "USING", "DESCRIBE", "SHOW", "USE", "DATABASE",
            "SCHEMA", "CASCADE", "RESTRICT", "IF", "ELSEIF", "LOOP", "WHILE", "FOR",
            "REPEAT", "UNTIL", "LEAVE", "ITERATE", "CALL", "RETURN", "SIGNAL", "RESIGNAL",
            "GET", "DIAGNOSTICS", "CONDITION", "HANDLER", "SQLSTATE", "FOUND", "ROW_COUNT"
    ));

    /**
     * 常见 SQL 函数名集合（大写形式），用于识别函数调用（如 {@code COUNT(}、{@code NOW()}）。
     */
    private static final Set<String> FUNCTIONS = new HashSet<>(Arrays.asList(
            "ABS", "ACOS", "ASIN", "ATAN", "ATAN2", "CEIL", "CEILING", "COS", "COT",
            "DEGREES", "EXP", "FLOOR", "LOG", "LOG10", "MOD", "PI", "POWER", "RADIANS",
            "RAND", "ROUND", "SIGN", "SIN", "SQRT", "TAN", "TRUNCATE", "ASCII", "CHAR",
            "CHAR_LENGTH", "CHARACTER_LENGTH", "CONCAT", "CONCAT_WS", "FIELD", "FIND_IN_SET",
            "FORMAT", "INSERT", "INSTR", "LCASE", "LEFT", "LENGTH", "LOCATE", "LOWER",
            "LPAD", "LTRIM", "MID", "POSITION", "REPEAT", "REPLACE", "REVERSE", "RIGHT",
            "RPAD", "RTRIM", "SPACE", "STRCMP", "SUBSTR", "SUBSTRING", "SUBSTRING_INDEX",
            "TRIM", "UCASE", "UPPER", "ADDDATE", "ADDTIME", "CURDATE", "CURTIME", "DATE",
            "DATEDIFF", "DATE_ADD", "DATE_FORMAT", "DATE_SUB", "DAY", "DAYNAME", "DAYOFMONTH",
            "DAYOFWEEK", "DAYOFYEAR", "EXTRACT", "FROM_DAYS", "FROM_UNIXTIME", "HOUR",
            "LAST_DAY", "MAKEDATE", "MAKETIME", "MICROSECOND", "MINUTE", "MONTH", "MONTHNAME",
            "NOW", "PERIOD_ADD", "PERIOD_DIFF", "QUARTER", "SECOND", "SEC_TO_TIME", "STR_TO_DATE",
            "SUBDATE", "SUBTIME", "SYSDATE", "TIME", "TIME_FORMAT", "TIME_TO_SEC", "TIMEDIFF",
            "TIMESTAMP", "TIMESTAMPADD", "TIMESTAMPDIFF", "TO_DAYS", "UNIX_TIMESTAMP", "UTC_DATE",
            "UTC_TIME", "UTC_TIMESTAMP", "WEEK", "WEEKDAY", "WEEKOFYEAR", "YEAR", "YEARWEEK",
            "BIN", "BINARY", "CAST", "COALESCE", "CONNECTION_ID", "CONV", "CONVERT", "DATABASE",
            "IF", "IFNULL", "ISNULL", "LAST_INSERT_ID", "NULLIF", "SESSION_USER", "SYSTEM_USER",
            "USER", "VERSION", "MD5", "SHA1", "SHA2", "AES_ENCRYPT", "AES_DECRYPT", "COMPRESS",
            "UNCOMPRESS", "CRC32", "ENCODE", "DECODE", "DES_ENCRYPT", "DES_DECRYPT", "ENCRYPT"
    ));

    /**
     * SQL 操作符集合（未在当前实现中直接使用，保留以备扩展）。
     */
    private static final Set<String> OPERATORS = new HashSet<>(Arrays.asList(
            "=", "!=", "<>", "<", ">", "<=", ">=", "+", "-", "*", "/", "%", "||", "&&", "!",
            "&", "|", "^", "~", "<<", ">>", "IS", "IS NOT", "IN", "NOT IN", "BETWEEN", "NOT BETWEEN",
            "LIKE", "NOT LIKE", "EXISTS", "NOT EXISTS", "ALL", "ANY", "SOME"
    ));

    /**
     * SQL 分词正则表达式，用于将 SQL 拆分为可识别的 token。
     * <p>匹配内容包括：
     * <ul>
     *   <li>关键字（如 SELECT、FROM）</li>
     *   <li>函数调用（如 {@code COUNT(}）</li>
     *   <li>字符串（单引号、双引号、反引号）</li>
     *   <li>注释（单行 {@code --} 和多行 {@code }）</li>
     *   <li>标点符号（如 {@code (}, {@code )}, {@code ,}, {@code ;}）</li>
     *   <li>参数占位符（如 {@code ??}）</li>
     * </ul>
     * </p>
     */
    private static final Pattern SQL_PATTERN = Pattern.compile(
            "(?i)(" +
                    "\\b(?:SELECT|FROM|WHERE|AND|OR|INSERT|UPDATE|DELETE|CREATE|DROP|ALTER|TABLE|JOIN|LEFT|RIGHT|INNER|OUTER|ON|AS|GROUP BY|ORDER BY|HAVING|LIMIT|OFFSET|UNION|VALUES|SET)\\b|" +
                    "\\b\\w+\\s*\\(" + // 函数
                    "|'[^']*'" + // 单引号字符串
                    "|\"[^\"]*\"" + // 双引号字符串
                    "|`[^`]*`" + // 反引号标识符
                    "|/\\*.*?\\*/" + // 多行注释
                    "|--.*$" + // 单行注释
                    "|[(),;.]" + // 标点符号
                    "|\\?\\?" + // 参数占位符
                    ")"
    );

    /**
     * 格式化过程中的状态跟踪器，用于管理缩进、换行、子查询嵌套等上下文信息。
     */
    private static class FormatState {
        /**
         * 当前缩进级别（每级缩进由 {@link FormatConfig} 控制）
         */
        int indentLevel = 0;
        /**
         * 是否需要在下一个 token 前插入换行
         */
        boolean needsNewLine = false;
        /**
         * 上一个处理的 token（大写形式）
         */
        String previousToken = "";
        /**
         * 当前是否处于子查询中
         */
        boolean inSubQuery = false;
    }

    /**
     * 使用默认配置格式化 SQL 语句。
     *
     * @param sql 需要格式化的原始 SQL 语句（可为 {@code null} 或空）
     * @return 格式化后的 SQL 字符串；若输入为 {@code null} 或空，则原样返回
     */
    public static String format(String sql) {
        return format(sql, FormatConfig.defaultConfig());
    }

    /**
     * 使用指定配置格式化 SQL 语句。
     *
     * @param sql    需要格式化的原始 SQL 语句
     * @param config 格式化配置（控制大小写、缩进、行宽等）
     * @return 格式化后的 SQL 字符串
     */
    public static String format(String sql, FormatConfig config) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }

        // 预处理：统一换行符、压缩空白
        sql = preprocessSql(sql);

        StringBuilder result = new StringBuilder();
        FormatState state = new FormatState();

        Matcher matcher = SQL_PATTERN.matcher(sql);
        int lastEnd = 0;

        while (matcher.find()) {
            // 处理非匹配文本（通常是标识符或数字）
            if (matcher.start() > lastEnd) {
                String between = sql.substring(lastEnd, matcher.start()).trim();
                if (!between.isEmpty()) {
                    appendText(result, between, state, config);
                }
            }

            String token = matcher.group();
            String upperToken = token.toUpperCase();

            // 处理注释
            if (token.startsWith("/*") || token.startsWith("--")) {
                appendComment(result, token, state, config);
                lastEnd = matcher.end();
                if (state.needsNewLine) {
                    result.append("\n");
                }
                continue;
            }

            // 处理字符串字面量（保留原样）
            if (token.startsWith("'") || token.startsWith("\"") || token.startsWith("`")) {
                appendText(result, token, state, config);
                lastEnd = matcher.end();
                continue;
            }

            // 处理关键字
            if (KEYWORDS.contains(upperToken)) {
                handleKeyword(result, token, upperToken, state, config);
            }
            // 处理函数（以 ( 结尾的标识符）
            else if (token.endsWith("(") && FUNCTIONS.contains(upperToken.replace("(", "").trim())) {
                result.append("\n");
                appendText(result, config.isUpperCase() ? upperToken : token, state, config);
            }
            // 处理标点符号
            else if (isPunctuation(token)) {
                handlePunctuation(result, token, state, config);
            }
            // 其他普通标识符或数字
            else {
                appendText(result, token, state, config);
            }

            state.previousToken = upperToken;
            lastEnd = matcher.end();
        }

        // 处理末尾剩余内容
        if (lastEnd < sql.length()) {
            String remaining = sql.substring(lastEnd).trim();
            if (!remaining.isEmpty()) {
                appendText(result, remaining, state, config);
            }
        }

        return result.toString().trim();
    }

    /**
     * 预处理 SQL：统一换行符为 {@code \n}，并将所有连续空白字符压缩为单个空格。
     *
     * @param sql 原始 SQL 字符串
     * @return 预处理后的 SQL 字符串
     */
    private static String preprocessSql(String sql) {
        sql = sql.replace("\r\n", "\n").replace("\r", "\n");
        // 每行独立处理：压缩行内空白，保留换行
        String[] lines = sql.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = lines[i].replaceAll("\\s+", " ").trim();
        }
        return String.join("\n", lines).trim();
    }

    /**
     * 处理 SQL 关键字的格式化逻辑。
     * <p>
     * - 主关键字（如 SELECT、FROM）：强制换行，并在子查询中缩进；
     * - 次要关键字（如 AND、OR）：仅添加前后空格。
     * </p>
     *
     * @param result     结果构建器
     * @param token      原始 token
     * @param upperToken 大写形式的 token
     * @param state      当前格式化状态
     * @param config     格式化配置
     */
    private static void handleKeyword(StringBuilder result, String token, String upperToken,
                                      FormatState state, FormatConfig config) {
        if (isMajorKeyword(upperToken)) {
            if (!state.previousToken.isEmpty()) {
                result.append("\n");
            }
            if (state.inSubQuery) {
                appendIndent(result, state.indentLevel, config);
            }
            result.append(config.isUpperCase() ? upperToken : token);
            result.append(" ");
            state.needsNewLine = true;
        } else {
            result.append(" ");
            result.append(config.isUpperCase() ? upperToken : token);
            result.append(" ");
        }
    }

    /**
     * 处理标点符号（括号、逗号、分号等）的格式化逻辑。
     * <p>
     * - {@code (}：若前一个 token 是 SELECT/INSERT 等，则视为子查询开始，增加缩进；
     * - {@code )}：若在子查询中，减少缩进；
     * - {@code ,}：在需要换行时，逗号后换行并缩进；
     * - {@code ;}：语句结束，重置状态。
     * </p>
     *
     * @param result 结果构建器
     * @param token  标点符号
     * @param state  当前格式化状态
     * @param config 格式化配置
     */
    private static void handlePunctuation(StringBuilder result, String token,
                                          FormatState state, FormatConfig config) {
        switch (token) {
            case "(":
                result.append(token);
                if (isSubqueryStarter(state.previousToken)) {
                    state.inSubQuery = true;
                    state.indentLevel++;
                    state.needsNewLine = true;
                }
                break;
            case ")":
                if (state.inSubQuery && state.indentLevel > 0) {
                    state.indentLevel--;
                    if (state.indentLevel == 0) {
                        state.inSubQuery = false;
                    }
                }
                result.append(token);
                break;
            case ",":
                result.append(token);
                if (state.needsNewLine) {
                    result.append("\n");
                    if (state.inSubQuery) {
                        appendIndent(result, state.indentLevel, config);
                    }
                } else {
                    result.append(" ");
                }
                break;
            case ";":
                result.append(token);
                result.append("\n");
                state.indentLevel = 0;
                state.inSubQuery = false;
                state.needsNewLine = false;
                break;
            default:
                result.append(token);
        }
    }

    /**
     * 将注释添加到结果中，并保持其原始格式（多行注释按行处理）。
     *
     * @param result  结果构建器
     * @param comment 注释内容（以 {@code /*} 或 {@code --} 开头）
     * @param state   当前格式化状态
     * @param config  格式化配置
     */
    private static void appendComment(StringBuilder result, String comment,
                                      FormatState state, FormatConfig config) {
        // 如果结果非空，说明前面有内容，注释应换行
        if (result.length() > 0) {
            result.append("\n");
            // 应用当前缩进（包括子查询或主查询缩进）
            appendIndent(result, state.indentLevel, config);
        }
        result.append(comment.trim());
        // 注释后通常需要换行（比如后面还有 WHERE）
        state.needsNewLine = true;
    }

    /**
     * 将普通文本（标识符、数字、未识别 token）添加到结果中。
     * <p>根据 {@code needsNewLine} 状态决定是否换行和缩进。</p>
     *
     * @param result 结果构建器
     * @param text   要添加的文本
     * @param state  当前格式化状态
     * @param config 格式化配置
     */
    private static void appendText(StringBuilder result, String text,
                                   FormatState state, FormatConfig config) {
        if (state.needsNewLine) {
            result.append("\n");
            if (state.inSubQuery) {
                appendIndent(result, state.indentLevel, config);
            }
            state.needsNewLine = false;
        } else {
            if (!result.toString().endsWith(".")) {
                result.append(" ");
            }
        }
        result.append(text);
    }

    /**
     * 向结果中追加指定级别的缩进。
     *
     * @param result      结果构建器
     * @param indentLevel 缩进级别（0 表示无缩进）
     * @param config      格式化配置（决定缩进字符和每级宽度）
     */
    private static void appendIndent(StringBuilder result, int indentLevel, FormatConfig config) {
        for (int i = 0; i < indentLevel * config.getIndentSize(); i++) {
            result.append(config.getIndentChar());
        }
    }

    /**
     * 判断是否为主关键字（需换行的关键字）。
     *
     * @param token 大写形式的 token
     * @return {@code true} 如果是主关键字，否则 {@code false}
     */
    private static boolean isMajorKeyword(String token) {
        return Arrays.asList("SELECT", "FROM", "WHERE", "JOIN", "LEFT", "RIGHT",
                "INNER", "OUTER", "GROUP BY", "ORDER BY", "HAVING",
                "UNION", "VALUES", "SET").contains(token);
    }

    /**
     * 判断前一个 token 是否表示子查询开始。
     *
     * @param token 大写形式的前一个 token
     * @return {@code true} 如果是子查询开始关键字，否则 {@code false}
     */
    private static boolean isSubqueryStarter(String token) {
        return Arrays.asList("SELECT", "INSERT", "UPDATE", "DELETE").contains(token);
    }

    /**
     * 判断 token 是否为标点符号。
     *
     * @param token 待检查的 token
     * @return {@code true} 如果是标点符号，否则 {@code false}
     */
    private static boolean isPunctuation(String token) {
        return Arrays.asList("(", ")", ",", ";", ".").contains(token);
    }

    /**
     * SQL 格式化配置类，支持链式调用。
     * <p>提供对关键字大小写、缩进、行宽等行为的细粒度控制。</p>
     */
    public static class FormatConfig {
        /**
         * 是否将关键字转换为大写
         */
        private boolean uppercase = true;
        /**
         * 每级缩进的空格数（默认 4）
         */
        private int indentSize = 2;
        /**
         * 缩进使用的字符（默认空格）
         */
        private char indentChar = ' ';
        /**
         * 最大行长度（当前未在格式化逻辑中使用，保留以备扩展）
         */
        private int maxLineLength = 80;

        /**
         * 获取默认配置：关键字大写、2 空格缩进、80 字符行宽。
         *
         * @return 默认配置实例
         */
        public static FormatConfig defaultConfig() {
            return new FormatConfig();
        }

        /**
         * 获取紧凑配置：关键字小写、2 空格缩进、120 字符行宽。
         *
         * @return 紧凑配置实例
         */
        public static FormatConfig compactConfig() {
            return new FormatConfig()
                    .setUppercase(false)
                    .setIndentSize(2)
                    .setMaxLineLength(120);
        }

        public boolean isUpperCase() {
            return uppercase;
        }

        public FormatConfig setUppercase(boolean uppercase) {
            this.uppercase = uppercase;
            return this;
        }

        public int getIndentSize() {
            return indentSize;
        }

        public FormatConfig setIndentSize(int indentSize) {
            this.indentSize = indentSize;
            return this;
        }

        public char getIndentChar() {
            return indentChar;
        }

        public FormatConfig setIndentChar(char indentChar) {
            this.indentChar = indentChar;
            return this;
        }

        public int getMaxLineLength() {
            return maxLineLength;
        }

        public FormatConfig setMaxLineLength(int maxLineLength) {
            this.maxLineLength = maxLineLength;
            return this;
        }
    }
}
