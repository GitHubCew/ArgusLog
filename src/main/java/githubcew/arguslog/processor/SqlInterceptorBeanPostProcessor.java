package githubcew.arguslog.processor;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.common.util.ProxyUtil;
import githubcew.arguslog.core.ArgusManager;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.ExecuteResult;
import githubcew.arguslog.monitor.MonitorInfo;
import githubcew.arguslog.monitor.MonitorSender;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import githubcew.arguslog.monitor.sql.DaoMethodDetector;
import githubcew.arguslog.monitor.sql.SqlFormatter;
import githubcew.arguslog.monitor.sql.SqlParameterFormatter;
import githubcew.arguslog.web.socket.ArgusSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * SQL 拦截器 Bean 后置处理器，用于在 Spring 容器中自动代理 {@link DataSource}，
 * 从而拦截所有通过 {@link PreparedStatement} 执行的 SQL 语句，并进行监控、格式化与实时推送。
 * <p>
 * 该组件通过 JDK 动态代理链式包装 {@code DataSource → Connection → PreparedStatement}，
 * 在 {@code execute*} 方法调用时捕获原始 SQL、绑定参数、执行耗时、调用栈上下文（DAO 方法、Web 请求方法），
 * 并将格式化后的 SQL 通过 WebSocket 推送给已订阅的监控用户。
 * </p>
 * <p>
 * 支持按用户配置的阈值（如执行时间）过滤慢 SQL，并区分成功/失败状态。
 * </p>
 *
 * <h2>工作流程</h2>
 * <ol>
 *   <li>拦截 Spring 容器中的 {@link DataSource} Bean</li>
 *   <li>为其创建代理，重写 {@code getConnection()}</li>
 *   <li>为返回的 {@link Connection} 创建代理，重写 {@code prepareStatement(sql)}</li>
 *   <li>为返回的 {@link PreparedStatement} 创建代理，拦截 {@code setXXX()} 和 {@code execute*()}</li>
 *   <li>在执行时收集参数、构建完整 SQL、获取调用上下文、计算耗时</li>
 *   <li>将结果通过 {@link MonitorSender} 异步推送给前端 WebSocket 客户端</li>
 * </ol>
 *
 * <h2>线程安全性</h2>
 * <div>本类是线程安全的：
 *   <ul>
 *     <li>使用 {@link ConcurrentHashMap} 存储 PreparedStatement 参数</li>
 *     <li>所有状态字段（如 {@code monitorSender}）在初始化后不再变更</li>
 *     <li>关键操作均包裹在 try-catch 中，避免影响主业务流程</li>
 *   </ul>
 * </div>
 *
 * <h2>优先级</h2>
 * <p>实现 {@link PriorityOrdered} 并返回 {@link Integer#MIN_VALUE}（即 {@code HIGHEST_PRECEDENCE}），
 * 确保在其他 BeanPostProcessor 之前执行，避免被其他代理包裹导致拦截失效。</p>
 *
 * @author chenenwei
 */
@Component
public class SqlInterceptorBeanPostProcessor implements BeanPostProcessor, PriorityOrdered {

    private static final Logger log = LoggerFactory.getLogger(SqlInterceptorBeanPostProcessor.class);

    /**
     * SQL 参数格式化器，用于将 Java 对象转换为可读的 SQL 字面量。
     */
    private final SqlParameterFormatter sqlParameterFormatter = new SqlParameterFormatter();

    /**
     * 监控消息发送器，用于异步提交监控任务。
     */
    private MonitorSender monitorSender;

    /**
     * WebSocket 消息处理器，用于向前端推送 SQL 监控信息。
     */
    private ArgusSocketHandler argusSocketHandler;

    /**
     * 在 Bean 初始化完成后进行处理。
     * <p>若检测到 {@link DataSource} 且非代理对象，则为其创建代理以实现 SQL 拦截。</p>
     *
     * @param bean     Spring 容器中的 Bean 实例
     * @param beanName Bean 名称
     * @return 若为原始 DataSource，则返回代理对象；否则原样返回
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof DataSource && !ProxyUtil.isProxy(bean)) {
            return createProxyDataSource((DataSource) bean);
        }
        return bean;
    }

    /**
     * 为原始 {@link DataSource} 创建代理，拦截 {@code getConnection()} 方法。
     *
     * @param original 原始 DataSource
     * @return 代理后的 DataSource
     */
    private DataSource createProxyDataSource(DataSource original) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        Connection conn = (Connection) method.invoke(original, args);
                        return createProxyConnection(conn);
                    }
                    return method.invoke(original, args);
                }
        );
    }

    /**
     * 为原始 {@link Connection} 创建代理，拦截 {@code prepareStatement(sql)} 方法。
     *
     * @param original 原始 Connection
     * @return 代理后的 Connection
     */
    private Connection createProxyConnection(Connection original) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class[]{Connection.class},
                (proxy, method, args) -> {
                    if ("prepareStatement".equals(method.getName()) && args != null && args.length > 0) {
                        String sql = (String) args[0];
                        PreparedStatement ps = (PreparedStatement) method.invoke(original, args);
                        return createProxyPreparedStatement(ps, sql);
                    }
                    return method.invoke(original, args);
                }
        );
    }

    /**
     * 为原始 {@link PreparedStatement} 创建代理，拦截参数设置和执行方法。
     * <p>
     * - 拦截所有 {@code setXXX(index, value)} 方法，记录参数值；
     * - 在 {@code execute}, {@code executeQuery}, {@code executeUpdate} 调用时：
     *   <ul>
     *     <li>记录开始时间</li>
     *     <li>执行原始方法</li>
     *     <li>构建完整 SQL（替换 ? 为实际值）</li>
     *     <li>获取调用上下文（Web 请求方法、DAO 方法）</li>
     *     <li>异步推送监控信息</li>
     *   </ul>
     * </p>
     *
     * @param original 原始 PreparedStatement
     * @param sql      原始 SQL 模板（含 ? 占位符）
     * @return 代理后的 PreparedStatement
     */
    private PreparedStatement createProxyPreparedStatement(PreparedStatement original, String sql) {
        // 使用线程安全的 Map 存储参数（index -> value）
        Map<Integer, Object> params = new ConcurrentHashMap<>();

        // 检测当前调用栈中的 DAO 方法
        DaoMethodDetector.MethodInfo daoInfo = DaoMethodDetector.detect();
        // 检测当前 Web 请求入口方法（如 Controller）
        Method startMethod = DaoMethodDetector.detectWebRequest();

        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();

                    // 拦截参数设置方法（如 setString, setInt 等）
                    if (methodName.startsWith("set") && args != null && args.length >= 2) {
                        // args[0] 是参数索引（从1开始），args[1] 是参数值
                        params.put((Integer) args[0], args[1]);
                    }

                    // 拦截执行方法
                    if ("execute".equals(methodName) || "executeQuery".equals(methodName) || "executeUpdate".equals(methodName)) {
                        long start = System.currentTimeMillis();

                        // 安全初始化依赖组件
                        safeInit();

                        try {
                            Object result = method.invoke(original, args);
                            String completeSql = buildCompleteSql(sql, params);

                            // 异步发送 SQL 监控信息（成功）
                            safeSendSql(completeSql, System.currentTimeMillis() - start, startMethod, daoInfo, false, null);
                            return result;
                        } catch (Exception e) {
                            String completeSql = buildCompleteSql(sql, params);
                            // 异步发送 SQL 监控信息（失败）
                            safeSendSql(completeSql, System.currentTimeMillis() - start, startMethod, daoInfo, true, e);
                            throw e; // 重新抛出异常，不影响业务
                        }
                    }

                    return method.invoke(original, args);
                }
        );
    }

    /**
     * 将原始 SQL 模板中的 {@code ?} 占位符替换为实际参数值。
     * <p>按参数索引升序替换，避免顺序错乱。</p>
     *
     * @param sql    原始 SQL（含 ? 占位符）
     * @param params 参数映射（索引从1开始）
     * @return 替换后的完整 SQL 字符串
     */
    private String buildCompleteSql(String sql, Map<Integer, Object> params) {
        if (params.isEmpty()) return sql;

        List<Map.Entry<Integer, Object>> sortedParams = params.entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toList());

        String result = sql;
        for (Map.Entry<Integer, Object> entry : sortedParams) {
            String strValue = this.sqlParameterFormatter.formatParameter(entry.getValue());
            // 使用 replaceFirst 避免替换已替换的内容（适用于多个 ?）
            result = result.replaceFirst("\\?", strValue);
        }
        return result;
    }

    /**
     * 返回最高优先级，确保在其他 BeanPostProcessor 之前执行。
     *
     * @return {@link Integer#MIN_VALUE}
     */
    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

    /**
     * 初始化监控依赖组件（懒加载）。
     * <p>从 Spring 上下文中获取 {@link ArgusManager} 和 {@link ArgusSocketHandler}。</p>
     */
    private void init() {
        if (this.monitorSender == null) {
            ArgusManager argusManager = ContextUtil.getBean(ArgusManager.class);
            this.monitorSender = argusManager.getMonitorSender();
        }
        if (this.argusSocketHandler == null) {
            this.argusSocketHandler = ContextUtil.getBean(ArgusSocketHandler.class);
        }
    }

    /**
     * 安全地初始化依赖组件，捕获异常避免中断主流程。
     */
    private void safeInit() {
        try {
            init();
        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.error("Argus => SQL拦截器初始化失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 安全地发送 SQL 监控信息。
     * <p>
     * 遍历所有订阅了 SQL 监控的用户，根据其配置的阈值过滤慢 SQL，
     * 并通过 WebSocket 异步推送格式化后的 SQL 信息。
     * </p>
     *
     * @param sql        完整的 SQL 语句
     * @param time       执行耗时（毫秒）
     * @param startMethod Web 请求入口方法（可能为 null）
     * @param daoInfo    DAO 方法信息
     * @param isError    是否执行出错
     */
    private void safeSendSql(String sql, Long time,
                             Method startMethod,
                             DaoMethodDetector.MethodInfo daoInfo,
                             boolean isError,
                             Throwable ex) {
        try {
            // 格式化 SQL（美化缩进）
            String formatSql = "\n" + SqlFormatter.format(sql);

            if (!Objects.isNull(monitorSender) && !Objects.isNull(argusSocketHandler)) {
                List<String> users = ArgusCache.getSqlMonitorUsers();
                for (String user : users) {
                    ArgusUser argusUser = ArgusCache.getUserToken(user);
                    if (argusUser == null || !argusUser.getSession().isOpen()) {
                        continue;
                    }

                    MonitorInfo.Sql userSqlMonitor = ArgusCache.getSqlMonitorByUser(user);
                    if (userSqlMonitor == null) {
                        continue;
                    }

                    // 仅推送超过阈值的 SQL
                    if (time < userSqlMonitor.getThreshold()) {
                        continue;
                    }

                    // 过滤不匹配的sql
                    // 过滤包
                    if (!Objects.isNull(userSqlMonitor.getPackageName()) && !daoInfo.getPackageName().contains(userSqlMonitor.getPackageName())) {
                        continue;
                    }

                    // 过滤类
                    if (!Objects.isNull(userSqlMonitor.getClassName())
                            && (!userSqlMonitor.getClassName().equals(daoInfo.getSimpleClassName())
                            || !daoInfo.getClassName().contains(userSqlMonitor.getClassName()))) {
                        continue;
                    }

                    // 过滤方法
                    if (!Objects.isNull(userSqlMonitor.getMethodNames()) && !userSqlMonitor.getMethodNames().contains(daoInfo.getMethodName())) {
                        continue;
                    }

                    // 构建监控消息
                    String message = "Argus SQL: \n";
                    if (startMethod != null) {
                        message += "start => "
                                + startMethod.getDeclaringClass().getSimpleName()
                                + "."
                                + startMethod.getName()
                                + "()"
                                + "\n";
                    }
                    message += "dao => "
                            + daoInfo.getSimpleClassName()
                            + "." + daoInfo.getMethodName()
                            + "()"
                            + "[" + time + "ms]\n";

                    message += "sql => \n";

                    message += OutputWrapper.wrapperCopy(formatSql);

                    message += "\n";

                    // 错误信息
                    if (isError) {
                        message += "error =>  \n"
                                + extractException(ex)
                                + "\n";
                    }

                    // 异步提交推送任务
                    String finalMessage = message;
                    monitorSender.submit(() -> {
                        String outMessage = isError
                                ? OutputWrapper.formatOutput(ExecuteResult.failed(finalMessage))
                                : OutputWrapper.formatOutput(ExecuteResult.success(finalMessage));
                        this.argusSocketHandler.send(argusUser.getSession(), outMessage);
                    });
                }
            }
        } catch (Exception e) {
            // 忽略推送过程中的异常，避免影响业务 SQL 执行
            log.debug("SQL 监控推送异常: {}", e.getMessage());
        }
    }

    /**
     * 追加异常堆栈
     *
     * @param e  异常
     * @return 堆栈信息
     */
    public String extractException(Throwable e) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}