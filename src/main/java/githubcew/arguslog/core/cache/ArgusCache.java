package githubcew.arguslog.core.cache;

import githubcew.arguslog.common.util.PatternUtil;
import githubcew.arguslog.core.account.Account;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Argus 缓存管理器
 * 用于存储和管理接口方法、用户监听关系、用户凭证等缓存信息
 *
 * @author chenenwei
 */
public class ArgusCache {

    /**
     * 接口方法缓存
     * key: 接口uri
     * value: 方法信息
     */
    private static final Map<String, ArgusMethod> uriMethodCache = new ConcurrentHashMap<>(256);

    /**
     * mq方法缓存
     * key: 方法信息
     * value: 队列列表
     */
    private static final Map<Method, List<String>> mqMethodCache = new ConcurrentHashMap<>(16);

    /**
     * 监听方法的用户列表
     * key: 方法信息
     * value: 用户token列表
     */
    private static final Map<ArgusMethod, List<String>> methodUsers = new ConcurrentHashMap<>(16);

    /**
     * 用户监听方法列表
     * key: 用户token
     * value: 监听方法信息列表
     */
    private static final Map<String, List<MonitorInfo>> userMonitorMethods = new ConcurrentHashMap<>(16);

    /**
     * 用户凭证信息
     * key: 用户token
     * value: 用户信息
     */
    private static final Map<String, ArgusUser> userTokens = new ConcurrentHashMap<>(16);

    /**
     * 用户追踪方法列表
     * key: 用户token
     * value: 追踪方法信息列表
     */
    private static final Map<String, List<MonitorInfo>> userTraceMethods = new ConcurrentHashMap<>(16);

    /**
     * 用户sql方法列表
     * key: 用户token
     * value: sql方法信息列表
     */
    private static final Map<String, MonitorInfo.Sql> userSqlMonitorMethods = new ConcurrentHashMap<>(16);

    /**
     * 用户mq方法列表
     * key: 用户token
     * value: 队列名称
     */
    private static final Map<String, String> userMqMonitorMethods = new ConcurrentHashMap<>(16);

    /**
     * 临时用户列表
     */
    private static final Set<Account> tempUsers = Collections.synchronizedSet(new HashSet<>());

    /**
     * 私有构造函数，防止实例化
     */
    private ArgusCache() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    // ==================== uriMethodCache 相关操作 ====================

    /**
     * 添加接口方法到缓存
     *
     * @param uri    接口uri
     * @param method 方法信息
     */
    public static void addUriMethod(String uri, ArgusMethod method) {
        uriMethodCache.put(uri, method);
    }

    /**
     * 检查是否存在指定URI
     *
     * @param uri 接口URI
     * @return 如果存在返回true，否则返回false
     */
    public static boolean hasUri(String uri) {
        return uriMethodCache.containsKey(uri);
    }

    /**
     * 根据URI获取方法信息
     *
     * @param uri 接口URI
     * @return 方法信息，如果不存在返回null
     */
    public static ArgusMethod getUriMethod(String uri) {
        return uriMethodCache.get(uri);
    }

    /**
     * 查询包含指定路径的接口URI列表
     *
     * @param uri 接口路径
     * @return 接口URI列表
     */
    public static List<String> getUris(String uri) {
        return uriMethodCache.keySet().stream()
                .filter(u -> u.contains(uri))
                .collect(Collectors.toList());
    }

    /**
     * 使用正则模式查询接口URI列表
     *
     * @param pattern 正则表达式模式
     * @return 接口URI列表
     */
    public static Map<String, Method> getUrisWithPattern(String pattern) {
        Map<String, Method> uriMap = new TreeMap<>();
        uriMethodCache.forEach((key, value) -> {
            if (PatternUtil.matchPattern(key, pattern)) {
                uriMap.put(key, value.getMethod());
            }
        });
        return uriMap;
    }

    /**
     * 根据方法获取URI
     *
     * @param method 方法信息
     * @return 接口URI，如果不存在返回null
     */
    public static String getMethodUri(ArgusMethod method) {
        if (Objects.isNull(method) || Objects.isNull(method.getMethod())) {
            return null;
        }

        return uriMethodCache.entrySet().stream()
                .filter(entry -> entry.getValue().getMethod().equals(method.getMethod()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有接口方法
     * @return 所有方法
     */
    public static List<ArgusMethod> getMethods() {
        return new ArrayList<>(uriMethodCache.values());
    }

    // ==================== methodUsers 相关操作 ====================

    /**
     * 为方法添加监听用户
     *
     * @param method 方法信息
     * @param user   用户token
     */
    public static void addMethodUser(ArgusMethod method, String user) {
        if (!containsMethod(method)) {
            methodUsers.put(method, new ArrayList<>(10));
        }
        if (!methodContainsUser(method, user)) {
            methodUsers.get(method).add(user);
        }
    }

    /**
     * 检查方法是否包含指定用户
     *
     * @param argusMethod 方法信息
     * @param argusUser   用户token
     * @return 如果包含返回true，否则返回false
     */
    public static boolean methodContainsUser(ArgusMethod argusMethod, String argusUser) {
        if (!containsMethod(argusMethod)) {
            return false;
        }
        return methodUsers.get(argusMethod).stream().anyMatch(user -> user.equals(argusUser));
    }

    /**
     * 检查是否包含指定方法
     *
     * @param argusMethod 方法信息
     * @return 如果包含返回true，否则返回false
     */
    public static boolean containsMethod(ArgusMethod argusMethod) {
        if (methodUsers.isEmpty()) {
            return false;
        }
        return methodUsers.keySet().stream()
                .anyMatch(method -> method.getMethod().equals(argusMethod.getMethod()));
    }

    /**
     * 检查是否包含指定方法
     *
     * @param method 方法对象
     * @return 如果包含返回true，否则返回false
     */
    public static boolean containsMethod(Method method) {
        if (methodUsers.isEmpty()) {
            return false;
        }
        return methodUsers.keySet().stream()
                .anyMatch(argusMethod -> method.equals(argusMethod.getMethod()));
    }

    /**
     * 从方法中移除用户
     *
     * @param argusMethod 方法信息
     * @param argusUser   用户token
     */
    public static void methodRemoveUser(ArgusMethod argusMethod, String argusUser) {
        if (!methodContainsUser(argusMethod, argusUser)) {
            return;
        }

        methodUsers.get(argusMethod).removeIf(user -> user.equals(argusUser));

        // 如果用户为空，则移除该方法
        if (methodUsers.get(argusMethod).isEmpty()) {
            methodUsers.remove(argusMethod);
        }
    }

    // ==================== userMonitorMethods 相关操作 ====================

    /**
     * 添加用户监测方法
     *
     * @param user        用户token
     * @param monitorInfo 监测信息
     */
    public static void addMonitorInfo(String user, MonitorInfo monitorInfo) {
        if (!containsUser(user)) {
            userMonitorMethods.put(user, new ArrayList<>(10));
        }

        // 添加监听方法
        if (!userContainsMethod(user, monitorInfo.getArgusMethod())) {
            userMonitorMethods.get(user).add(monitorInfo);
        }

        // 更新监听方法
        updateUserMethod(user, monitorInfo);

        // 添加到方法用户列表
        if (!methodContainsUser(monitorInfo.getArgusMethod(), user)) {
            addMethodUser(monitorInfo.getArgusMethod(), user);
        }
    }

    /**
     * 使用正则模式添加用户监测方法
     *
     * @param user        用户token
     * @param monitorInfo 监测信息
     * @param pattern     正则表达式模式
     */
    public static void addMonitorInfo(String user, MonitorInfo monitorInfo, String pattern) {
        if (!containsUser(user)) {
            userMonitorMethods.put(user, new ArrayList<>(uriMethodCache.size()));
        }

        uriMethodCache.forEach((uri, method) -> {
            if (!PatternUtil.match(uri, pattern)) {
                return;
            }

            if (!methodUsers.containsKey(method)) {
                methodUsers.put(method, new ArrayList<>(1));
            }
            methodUsers.get(method).add(user);

            List<MonitorInfo> monitorInfos = userMonitorMethods.get(user);
            MonitorInfo monitor = new MonitorInfo();
            monitorInfos.removeIf(m -> m.getArgusMethod().equals(method));
            BeanUtils.copyProperties(monitorInfo, monitor);
            monitor.setArgusMethod(method);
            monitorInfos.add(monitor);
        });
    }

    /**
     * 更新用户监听方法
     *
     * @param user        用户token
     * @param monitorInfo 监控方法信息
     */
    public static void updateUserMethod(String user, MonitorInfo monitorInfo) {
        List<MonitorInfo> monitorInfos = userMonitorMethods.get(user);
        if (monitorInfos == null) {
            return;
        }

        ListIterator<MonitorInfo> iterator = monitorInfos.listIterator();
        while (iterator.hasNext()) {
            MonitorInfo monitor = iterator.next();
            if (monitor.getArgusMethod().equals(monitorInfo.getArgusMethod())) {
                iterator.set(monitorInfo);
            }
        }
    }

    /**
     * 检查用户是否包含指定方法
     *
     * @param argusUser   用户token
     * @param argusMethod 方法信息
     * @return 如果包含返回true，否则返回false
     */
    public static boolean userContainsMethod(String argusUser, ArgusMethod argusMethod) {
        if (!containsUser(argusUser)) {
            return false;
        }
        if (Objects.isNull(argusMethod) || Objects.isNull(argusMethod.getMethod())) {
            return false;
        }

        return userMonitorMethods.get(argusUser).stream()
                .anyMatch(monitor -> monitor.getArgusMethod().getMethod().equals(argusMethod.getMethod()));
    }

    /**
     * 检查是否包含指定用户
     *
     * @param argusUser 用户token
     * @return 如果包含返回true，否则返回false
     */
    public static boolean containsUser(String argusUser) {
        if (userMonitorMethods.isEmpty()) {
            return false;
        }
        return userMonitorMethods.containsKey(argusUser);
    }

    /**
     * 移除用户监听方法
     *
     * @param argusUser   用户token
     * @param argusMethod 方法信息
     */
    public static void userRemoveMethod(String argusUser, ArgusMethod argusMethod) {
        if (!userContainsMethod(argusUser, argusMethod)) {
            return;
        }

        userMonitorMethods.get(argusUser)
                .removeIf(monitor -> monitor.getArgusMethod().getMethod().equals(argusMethod.getMethod()));

        // 如果方法为空，则移除该用户
        if (methodUsers.get(argusMethod).isEmpty()) {
            methodUsers.remove(argusMethod);
        }
    }

    /**
     * 移除用户所有监听方法
     *
     * @param argusUser 用户token
     */
    public static void userRemoveAllMethod(String argusUser) {
        if (containsUser(argusUser)) {
            userMonitorMethods.remove(argusUser);
        }

        // 删除方法监听用户
        methodUsers.forEach((method, users) -> users.removeIf(user -> user.equals(argusUser)));
    }

    /**
     * 根据正则模式移除用户监听方法
     *
     * @param argusUser 用户token
     * @param pattern   正则表达式模式
     */
    public static void removeMonitorMethodWithPattern(String argusUser, String pattern) {
        List<MonitorInfo> monitorInfos = userMonitorMethods.get(argusUser);
        if (monitorInfos == null) {
            return;
        }

        monitorInfos.removeIf(monitorInfo -> PatternUtil.match(monitorInfo.getArgusMethod().getUri(), pattern));

        if (monitorInfos.isEmpty()) {
            userMonitorMethods.remove(argusUser);
            // 删除方法监听用户
            methodUsers.forEach((method, users) -> {
                users.removeIf(user -> user.equals(argusUser));
                if (methodUsers.get(method).size() == 0) {
                    methodUsers.remove(method);
                }
            });
        }
    }

    /**
     * 根据方法获取用户监测信息映射
     *
     * @param method 方法对象
     * @return 用户token到监测信息的映射
     */
    public static Map<String, MonitorInfo> getUsersByMethod(Method method) {
        Map<String, MonitorInfo> userMonitorMap = new HashMap<>();

        userMonitorMethods.forEach((user, methods) -> {
            Optional<MonitorInfo> first = methods.stream()
                    .filter(monitor -> monitor.getArgusMethod().getMethod().equals(method))
                    .findFirst();
            first.ifPresent(monitorInfo -> userMonitorMap.put(user, monitorInfo));
        });

        return userMonitorMap;
    }

    /**
     * 查询用户监听接口列表
     *
     * @param argusUser 用户token
     * @param pattern   正则表达式模式
     * @return 接口URI列表
     */
    public static Map<String, Method> getUserMonitorUris(String argusUser, String pattern) {
        if (!containsUser(argusUser)) {
            return new TreeMap<>();
        }

        List<MonitorInfo> monitorInfos = userMonitorMethods.get(argusUser);

        return monitorInfos.stream()
                .filter(monitor -> Objects.isNull(pattern) ||
                        PatternUtil.match(monitor.getArgusMethod().getUri(), pattern))
                .sorted(Comparator.comparing(monitor -> monitor.getArgusMethod().getUri())) // 按URI排序
                .collect(Collectors.toMap(
                        monitor -> monitor.getArgusMethod().getUri(),
                        monitor -> monitor.getArgusMethod().getMethod(),
                        (existing, replacement) -> existing,
                        TreeMap::new
                ));
    }

    // ==================== userTokens 相关操作 ====================

    /**
     * 添加用户凭证
     *
     * @param token     用户token
     * @param argusUser 用户信息
     */
    public static void addUserToken(String token, ArgusUser argusUser) {
        userTokens.put(token, argusUser);
    }

    /**
     * 根据token获取用户信息
     *
     * @param token 用户token
     * @return 用户信息，如果不存在返回null
     */
    public static ArgusUser getUserToken(String token) {
        return userTokens.get(token);
    }

    /**
     * 根据用户名获取用户信息
     *
     * @param username 用户token
     * @return 用户信息，如果不存在返回null
     */
    public static ArgusUser getUserByUsername(String username) {
        return userTokens.values().stream()
                .filter(user -> user.getAccount().getUsername().equals(username))
                .findFirst()
                .orElse(null);
    }

    /**
     * 检查是否存在指定token
     *
     * @param token 用户token
     * @return 如果存在返回true，否则返回false
     */
    public static boolean hasToken(String token) {
        if (Objects.isNull(token) || token.isEmpty()) {
            return false;
        }
        return userTokens.containsKey(token);
    }

    /**
     * 移除用户token
     *
     * @param token 用户token
     */
    public static void removeUserToken(String token) {
        userTokens.remove(token);
    }

    /**
     * 根据WebSocket会话获取用户信息
     *
     * @param session WebSocket会话
     * @return 用户信息，如果不存在返回null
     */
    public static ArgusUser getUserBySession(WebSocketSession session) {
        return userTokens.values().stream()
                .filter(user -> user.getSession() != null && user.getSession().equals(session))
                .findFirst()
                .orElse(null);
    }

    /**
     * 统计在线用户数
     *
     * @return 在线用户数量
     */
    public static int countOnlineUser() {
        return userTokens.size();
    }

    /**
     * 移除过期的凭证
     */
    public static void clearExpiredToken() {
        userTokens.entrySet().removeIf(entry -> {
            ArgusUser user = entry.getValue();
            if (user.getToken().getExpireTime() < System.currentTimeMillis()) {
                String token = user.getToken().getToken();

                // 删除方法监听用户
                methodUsers.entrySet().removeIf(methodEntry -> {
                    methodEntry.getValue().removeIf(u -> u.equals(token));
                    return methodEntry.getValue().isEmpty();
                });

                // 删除用户监听方法
                userMonitorMethods.keySet().removeIf(u -> u.equals(token));

                // 删除监听trace方法用户
                userTraceMethods.keySet().removeIf(u -> u.equals(token));

                // 移除用户sql
                userSqlMonitorMethods.remove(token);

                // 移除用户mq监听
                userMqMonitorMethods.remove(token);
                return true;
            }
            return false;
        });
    }

    /**
     * 移除过期的凭证
     * @param token 用户token
     */
    public static void clearUserToken(String token) {

        userTokens.entrySet().removeIf(entry -> {

            if (entry.getValue().getToken().getToken().equals(token)) {
                // 删除方法监听用户
                methodUsers.entrySet().removeIf(methodEntry -> {
                    methodEntry.getValue().removeIf(u -> u.equals(token));
                    return methodEntry.getValue().isEmpty();
                });

                // 删除用户监听方法
                userMonitorMethods.keySet().removeIf(u -> u.equals(token));

                // 删除监听trace方法用户
                userTraceMethods.keySet().removeIf(u -> u.equals(token));

                // 移除用户sql
                userSqlMonitorMethods.remove(token);

                // 移除用户mq监听
                userMqMonitorMethods.remove(token);
                return true;
            }
            return false;
        });
    }

    // ==================== userTraceMethods 相关操作 ====================

    /**
     * 添加用户追踪方法
     *
     * @param user        用户token
     * @param monitorInfo 监测信息
     */
    public static void addUserTraceMethod(String user, MonitorInfo monitorInfo) {
        userTraceMethods.computeIfAbsent(user, k -> new ArrayList<>());

        Optional<MonitorInfo> first = userTraceMethods.get(user)
                .stream()
                .filter(monitor -> monitor.getArgusMethod().getMethod().equals(monitorInfo.getArgusMethod().getMethod()))
                .findFirst();
        first.ifPresent(info -> userTraceMethods.get(user).remove(info));
        userTraceMethods.get(user).add(monitorInfo);
    }

    /**
     * 获取追踪用户数量
     *
     * @return 追踪用户数量
     */
    public static int countTraceUser() {
        return userTraceMethods.size();
    }

    /**
     * 根据方法获取追踪用户列表
     *
     * @param argusMethod 方法信息
     * @return 用户token列表
     */
    public static List<String> getTraceUsersByMethod(ArgusMethod argusMethod) {
        if (userTraceMethods.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> users = new ArrayList<>();
        userTraceMethods.forEach((user, methods) -> {
            boolean hasMethod = methods.stream()
                    .anyMatch(monitor -> monitor.getArgusMethod().getMethod().equals(argusMethod.getMethod()));
            if (hasMethod) {
                users.add(user);
            }
        });
        return users;
    }

    /**
     * 获取所有监听开始的方法
     * @return 方法列表
     */
    public static List<Method> getTraceStartMethods () {
        return userTraceMethods.values()
                .stream()
                .flatMap(methods -> methods.stream().map(monitor -> monitor.getArgusMethod().getMethod()))
                .collect(Collectors.toList());
    }

    /**
     * 根据用户和URI获取追踪监测信息
     *
     * @param user 用户token
     * @param method  方法
     * @return 监测信息，如果不存在返回null
     */
    public static MonitorInfo getTraceMonitorByUser(String user, Method method) {
        if (!userTraceMethods.containsKey(user)) {
            return null;
        }

        Optional<MonitorInfo> first = userTraceMethods.get(user).stream()
                .filter(monitor -> monitor.getArgusMethod().getMethod().equals(method))
                .findFirst();
        return first.orElse(null);
    }

    /**
     * 根据用户获取自己追踪但其他人未监测信息
     *
     * @param user 用户token
     * @return 监测信息，如果不存在返回null
     */
    public static List<MonitorInfo> getTraceMonitorAndNoOtherByUser(String user) {
        if (!userTraceMethods.containsKey(user)) {
            return null;
        }

        // 计算其他用户监测的所有方法
        Set<Method> otherUsersMethods = userTraceMethods.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(user))
                .flatMap(entry -> entry.getValue().stream())
                .map(monitor -> monitor.getArgusMethod().getMethod())
                .collect(Collectors.toSet());

        // 只保留当前用户独有监测的方法
        return userTraceMethods.get(user).stream()
                .filter(monitor -> !otherUsersMethods.contains(monitor.getArgusMethod().getMethod()))
                .collect(Collectors.toList());
    }

    /**
     * 查询用户追踪接口列表
     *
     * @param argusUser 用户token
     * @return 接口URI列表
     */
    public static List<String> getTraceUriByUser(String argusUser) {
        if (userTraceMethods.isEmpty() || !userTraceMethods.containsKey(argusUser)) {
            return new ArrayList<>(0);
        }
        return userTraceMethods.get(argusUser).stream()
                .map(monitor -> monitor.getArgusMethod().getUri())
                .collect(Collectors.toList());
    }

    /**
     * 移除用户追踪方法
     *
     * @param argusUser   用户token
     * @param argusMethod 方法信息
     */
    public static void userRemoveTraceMethod(String argusUser, ArgusMethod argusMethod) {
        if (userTraceMethods.isEmpty() || !userTraceMethods.containsKey(argusUser)) {
            return;
        }

        // 移除用户监听的方法
        userTraceMethods.get(argusUser)
                .removeIf(monitor -> monitor.getArgusMethod().getMethod().equals(argusMethod.getMethod()));

        // 用户监听方法为空，则移除用户
        if (userTraceMethods.get(argusUser).isEmpty()) {
            userTraceMethods.remove(argusUser);
        }
    }

    /**
     * 移除用户所有追踪方法
     *
     * @param argusUser 用户token
     */
    public static void userRemoveAllTraceMethod(String argusUser) {
        if (userTraceMethods.isEmpty() || !userTraceMethods.containsKey(argusUser)) {
            return;
        }
        userTraceMethods.get(argusUser).clear();
    }

    /**
     * 检查用户是否已经监听了指定URI的调用链
     *
     * @param token 用户token
     * @param uri   接口URI
     * @return 如果已监听返回true，否则返回false
     */
    public static boolean userHasTraceUri(String token, String uri) {
        if (userTraceMethods.isEmpty() || !userTraceMethods.containsKey(token)) {
            return false;
        }
        return userTraceMethods.get(token).stream()
                .anyMatch(monitor -> monitor.getArgusMethod().getUri().equals(uri));
    }

    // ==================== userSql 相关操作 ====================

    /**
     * 增加用户SQL监听
     * @param token 用户token
     * @param SqlMonitor 监听信息
     */
    public static void addUserSqlMonitor(String token, MonitorInfo.Sql SqlMonitor) {
        userSqlMonitorMethods.put(token, SqlMonitor);
    }

    /**
     * 移除用户SQL监听
     * @param token 用户token
     */
    public static void removeUserSqlMonitor(String token) {
        if (userSqlMonitorMethods.isEmpty() || !userSqlMonitorMethods.containsKey(token)) {
            return;
        }
        userSqlMonitorMethods.remove(token);
    }

    /**
     * 获取所有用户SQL监听用户
     * @return 用户列表
     */
    public static List<String> getSqlMonitorUsers() {
        return new ArrayList<>(userSqlMonitorMethods.keySet());
    }

    /**
     * 获取用户SQL监听信息
     * @param token 用户token
     * @return 监听信息，如果用户不存在返回null
     */
    public static MonitorInfo.Sql getSqlMonitorByUser(String token) {
        return userSqlMonitorMethods.get(token);
    }


    /**
     * 获取所有在线用户
     * @return 用户列表
     */
    public static Set<String> getAllOnlineUser () {

        return userTokens.values().stream().map(user -> user.getAccount().getUsername()).collect(Collectors.toSet());
    }

    // ==================== MQ 相关操作 ====================

    /**
     * 注册 MQ 监听方法及其监听的队列列表。
     * <p>
     * 将指定方法与一个或多个队列名称关联，用于后续监控匹配。
     * 若方法已存在，则追加队列名称（去重由调用方保证）。
     * </p>
     *
     * @param method 监听方法（通常带有 @RabbitListener 等注解）
     * @param queues 该方法监听的队列或主题名称列表，不可为 null
     */
    public static void addMqMethod(Method method, List<String> queues) {
        mqMethodCache.putIfAbsent(method, new ArrayList<>());
        mqMethodCache.get(method).addAll(queues);
    }

    /**
     * 获取订阅了指定监听方法所关联队列的所有用户 Token 列表。
     * <p>
     * 通过比对 {@code userMqMonitorMethods} 中用户订阅的队列与 {@code mqMethodCache} 中方法监听的队列，
     * 返回所有匹配的用户标识。
     * </p>
     *
     * @param method 目标监听方法
     * @return 订阅了该方法任一队列的用户 Token 列表；若无订阅，返回空列表
     */
    public static List<String> getMqMonitorUser(Method method) {
        List<String> monitorUser = new ArrayList<>();
        userMqMonitorMethods.forEach((token, queue) -> {
            for (Map.Entry<Method, List<String>> entry : mqMethodCache.entrySet()) {

                if (!entry.getValue().contains(queue)) {
                    continue;
                }
                if (!Objects.isNull(method) && entry.getKey().equals(method)) {
                    monitorUser.add(token);
                }
            }
        });
        return monitorUser;
    }

    /**
     * 构建队列名称到监听方法的反向映射。
     * <p>
     * 将内部缓存 {@code mqMethodCache}（方法 → 队列列表）转换为
     * {@code Map<queueName, Method>}，便于通过队列快速定位监听方法。
     * </p>
     * <p>
     * <strong>注意：</strong>若多个方法监听同一队列，后者会覆盖前者。
     * </p>
     *
     * @return 队列名称到监听方法的映射
     */
    public static Map<String, Method> getMethodQueue() {
        Map<String, Method> methodQueue = new HashMap<>();
        mqMethodCache.forEach((method, queues) -> {
            for (String queue : queues) {
                methodQueue.put(queue, method);
            }
        });
        return methodQueue;
    }

    /**
     * 获取指定监听方法所监听的所有队列名称。
     *
     * @param method 监听方法
     * @return 队列名称列表；若方法未注册，返回 null
     */
    public static List<String> getMethodQueues(Method method) {
        return mqMethodCache.get(method);
    }

    /**
     * 为指定用户订阅某个 MQ 队列的监控。
     * <p>
     * 用户将收到该队列上消息消费的日志推送。
     * </p>
     *
     * @param token 用户唯一标识（如 WebSocket 会话 ID 或用户 ID）
     * @param queue 队列或主题名称
     */
    public static void addMqMonitor(String token, String queue) {
        userMqMonitorMethods.put(token, queue);
    }

    /**
     * 取消指定用户的 MQ 监控订阅。
     *
     * @param token 用户唯一标识
     */
    public static void removeMqMonitor(String token) {
        userMqMonitorMethods.remove(token);
    }

    // ==================== 临时用户操作 ====================

    /**
     * 增加临时用户
     * @param account 账号信息
     */
    public static void addTempUser(Account account) {
        if (tempUsers.stream().anyMatch(tempUser -> tempUser.getUsername().equals(account.getUsername()))) {
            return;
        }
        tempUsers.add(account);
    }

    /**
     * 获取临时用户
     * @param username 用户名
     * @return 临时用户信息，如果用户不存在返回null
     */
    public static Account getTempUser(String username) {
        if (Objects.isNull(username)) {
            return null;
        }
        return tempUsers.stream().filter(tempUser -> tempUser.getUsername().equals(username)).findFirst().orElse(null);
    }

    /**
     * 获取所有临时用户
     * @return 临时用户列表
     */
    public static List<Account> getTempUsers() {
        return new ArrayList<>(tempUsers);
    }

    /**
     * 移除临时用户
     * @param username 用户名
     */
    public static void removeTempUser(String username) {
        if (Objects.isNull(username)) {
            return;
        }
        tempUsers.removeIf(tempUser -> tempUser.getUsername().equals(username));
    }
}