package githubcew.arguslog.core.cache;

import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
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
    public static List<String> getUrisWithPattern(String pattern) {
        return uriMethodCache.keySet().stream()
                .filter(u -> match(u, pattern))
                .collect(Collectors.toList());
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
            if (!match(uri, pattern)) {
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

        monitorInfos.removeIf(monitorInfo -> match(monitorInfo.getArgusMethod().getUri(), pattern));

        if (monitorInfos.isEmpty()) {
            userMonitorMethods.remove(argusUser);
            // 删除方法监听用户
            methodUsers.forEach((method, users) -> users.removeIf(user -> user.equals(argusUser)));
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
    public static List<String> getUserMonitorUris(String argusUser, String pattern) {
        if (!containsUser(argusUser)) {
            return new ArrayList<>();
        }

        List<MonitorInfo> monitorInfos = userMonitorMethods.get(argusUser);
        if (Objects.isNull(pattern)) {
            return monitorInfos.stream()
                    .map(monitor -> monitor.getArgusMethod().getUri())
                    .sorted()
                    .collect(Collectors.toList());
        }

        return monitorInfos.stream()
                .filter(monitor -> match(monitor.getArgusMethod().getUri(), pattern))
                .map(monitor -> monitor.getArgusMethod().getUri())
                .sorted()
                .collect(Collectors.toList());
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
                .filter(user -> user.getSession().equals(session))
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

    // ==================== userTraceInfo 相关操作 ====================
    // 注：userTraceInfo 目前没有公开的操作方法，可以根据需要添加

    // ==================== 通用工具方法 ====================

    /**
     * 检查URI是否匹配模式
     *
     * @param uri     接口URI
     * @param pattern 正则表达式模式
     * @return 如果匹配返回true，否则返回false
     */
    public static boolean match(String uri, String pattern) {
        if (!pattern.contains("*")) {
            return uri.equalsIgnoreCase(pattern);
        }
        return matchPattern(uri.toLowerCase(), pattern.toLowerCase());
    }

    /**
     * 使用正则模式匹配URI
     *
     * @param uri     接口URI
     * @param pattern 正则表达式模式
     * @return 如果匹配返回true，否则返回false
     */
    public static boolean matchPattern(String uri, String pattern) {
        String regex = "^" + escapeAndReplaceWildcard(pattern) + "$";
        return Pattern.matches(regex, uri);
    }

    /**
     * 转义和替换通配符
     *
     * @param pattern 原始模式字符串
     * @return 处理后的正则表达式字符串
     */
    private static String escapeAndReplaceWildcard(String pattern) {
        StringBuilder sb = new StringBuilder();
        for (char c : pattern.toCharArray()) {
            if (c == '*') {
                sb.append(".*");
            } else if ("\\[]^$.{}?+|()".indexOf(c) != -1) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}