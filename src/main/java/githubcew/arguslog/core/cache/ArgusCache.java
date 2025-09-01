package githubcew.arguslog.core.cache;

import githubcew.arguslog.common.util.CommonUtil;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorInfo;
import githubcew.arguslog.monitor.trace.asm.MethodCallInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Argus cache
 *
 * @author chenenwei
 */
public class ArgusCache {

    /**
     * 接口方法缓存
     * key: 接口uri
     * value: 方法
     */
    private final static Map<String, ArgusMethod> uriMethodCache;

    /**
     * 监听方法的用户列表
     * key: 方法
     * vaLue: 用户列表
     */
    private final static Map<ArgusMethod, List<String>> methodUsers;

    /**
     * 用户监听方法列表
     * key: 用户token
     * value: 监听方法列表
     */
    private final static Map<String, List<MonitorInfo>> userMonitorMethods;

    /**
     * 用户tokens
     * key: 用户token
     * value: 用户信息
     */
    private final static Map<String, ArgusUser> userTokens;

    /**
     * 用户trace方法
     * key: 用户token
     * value:  方法
     */
    private final static Map<String, List<ArgusMethod>> userTraceMethods;

    static {
        uriMethodCache = new ConcurrentHashMap<>(256);
        userMonitorMethods = new ConcurrentHashMap<>(1);
        methodUsers = new ConcurrentHashMap<>(1);
        userTokens = new ConcurrentHashMap<>(1);
        userTraceMethods = new ConcurrentHashMap<>(1);
    }

    /**
     * 添加接口方法
     *
     * @param uri    接口uri
     * @param method 方法
     */
    public static void addUriMethod(String uri, ArgusMethod method) {
        uriMethodCache.put(uri, method);
    }

    /**
     * 增加方法监测用户
     *
     * @param method 方法
     * @param user   用户
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
     * 添加用户监测方法
     *
     * @param user        用户
     * @param monitorInfo 监测信息
     */
    public static void addMonitorInfo(String user, MonitorInfo monitorInfo) {
        if (!containsUser(user)) {
            userMonitorMethods.put(user, new ArrayList<>(10));
        }
        // 添加监听方法
        if (!userContainsMethod(user, monitorInfo.getMethod())) {
            userMonitorMethods.get(user).add(monitorInfo);
        }
        // 更新监听方法
        updateUserMethod(user, monitorInfo);

        // 添加到
        if (!methodContainsUser(monitorInfo.getMethod(), user)) {
            addMethodUser(monitorInfo.getMethod(), user);
        }
    }

    /**
     * 添加用户监测方法
     * @param user 用户
     * @param monitorInfo 监测信息
     * @param pattern 正则
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
            monitorInfos.removeIf(m -> m.getMethod().equals(method));
            BeanUtils.copyProperties(monitorInfo, monitor);
            monitor.setMethod(method);
            monitorInfos.add(monitor);
        });
    }

    /**
     * 添加用户凭证
     *
     * @param token token
     * @param argusUser 用户
     */
    public static void addUserToken(String token, ArgusUser argusUser) {
        userTokens.put(token, argusUser);
    }

    /**
     * 根据token获取用户信息
     *
     * @param token token
     * @return 用户
     */
    public static ArgusUser getUserToken(String token) {
        return userTokens.get(token);
    }

    /**
     * 更新用户监听方法
     *
     * @param user        用户
     * @param monitorInfo 监控方法
     */
    public static void updateUserMethod(String user, MonitorInfo monitorInfo) {
        ListIterator<MonitorInfo> monitorInfoListIterator = userMonitorMethods.get(user).listIterator();
        while (monitorInfoListIterator.hasNext()) {
            MonitorInfo monitor = monitorInfoListIterator.next();
            if (monitor.getMethod().equals(monitorInfo.getMethod())) {
                monitorInfoListIterator.set(monitorInfo);
            }
        }
    }

    public static void addUserTraceMethod(String user, ArgusMethod method) {
        if (!userTraceMethods.containsKey(user)) {
            userTraceMethods.put(user, new ArrayList<>());
        }
        if (userHasTraceUri(user, method.getUri())) {
            return;
        }
        userTraceMethods.get(user).add(method);
    }

    /**
     * 是否包含用户
     * @param argusMethod 方法
     * @param argusUser 用户
     * @return 结果
     */
    public static boolean methodContainsUser(ArgusMethod argusMethod, String argusUser) {
        if (!containsMethod(argusMethod)) {
            return false;
        }
        return methodUsers.get(argusMethod).stream().anyMatch(user -> user.equals(argusUser));
    }

    /**
     * 用户是否包含某个方法
     *
     * @param argusUser   用户
     * @param argusMethod 方法
     * @return 结果
     */
    public static boolean userContainsMethod(String argusUser, ArgusMethod argusMethod) {

        if (!containsUser(argusUser)) {
            return false;
        }
        if (Objects.isNull(argusMethod) || Objects.isNull(argusMethod.getMethod())) {
            return false;
        }
        return userMonitorMethods.get(argusUser).stream().anyMatch(monitor -> monitor.getMethod().getMethod().equals(argusMethod.getMethod()));
    }

    /**
     * 是否包含方法
     *
     * @param argusMethod 方法
     * @return 结果
     */
    public static boolean containsMethod(ArgusMethod argusMethod) {
        if (methodUsers.isEmpty()) {
            return false;
        }
        return methodUsers.keySet().stream().anyMatch(method -> method.getMethod().equals(argusMethod.getMethod()));
    }

    /**
     * 是否包含方法
     *
     * @param method 方法
     * @return 结果
     */
    public static boolean containsMethod(Method method) {
        if (methodUsers.isEmpty()) {
            return false;
        }
        return methodUsers.keySet().stream().anyMatch(argusMethod -> method.equals(argusMethod.getMethod()));
    }

    public static int countTraceUser () {
        return userTraceMethods.size();
    }

    public static List<String> getTraceUsersByMethod(ArgusMethod argusMethod) {
        if (userTraceMethods.isEmpty()) {
            return new ArrayList<>();
        }
        List<String> users = new ArrayList<>();
        userTraceMethods.forEach((user, methods) -> {
            boolean hasMethod = methods.stream().anyMatch(method -> method.getMethod().equals(argusMethod.getMethod()));
            if (hasMethod) {
                users.add(user);
            }
        });
        return users;
    }

    /**
     * 查询用户追踪接口
     * @param argusUser 用户
     * @return 接口列表
     */
    public static List<String> getTraceUriByUser(String argusUser) {
        if (userTraceMethods.isEmpty() || !userTraceMethods.containsKey(argusUser)) {
            return new ArrayList<>(0);
        }
        return userTraceMethods.get(argusUser).stream().map(ArgusMethod::getUri).collect(Collectors.toList());
    }


    /**
     * 是否包含用户
     *
     * @param argusUser 用户
     * @return 结果
     */
    public static boolean containsUser(String argusUser) {
        if (userMonitorMethods.isEmpty()) {
            return false;
        }
        return userMonitorMethods.keySet().stream().anyMatch(user -> user.equals(argusUser));
    }

    /**
     * 移除监听方法用户
     *
     * @param argusMethod 方法
     * @param argusUser   用户
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

    /**
     * 移除用户监听方法
     *
     * @param argusUser   用户
     * @param argusMethod 方法
     */
    public static void userRemoveMethod(String argusUser, ArgusMethod argusMethod) {
        if (!userContainsMethod(argusUser, argusMethod)) {
            return;
        }
        userMonitorMethods.get(argusUser).removeIf(monitor -> monitor.getMethod().getMethod().equals(argusMethod.getMethod()));
        // 如果方法为空，则移除该用户
        if (methodUsers.get(argusMethod).isEmpty()) {
            methodUsers.remove(argusMethod);
        }
    }

    /**
     * 移除用户监听方法
     *
     * @param argusUser   用户
     * @param argusMethod 方法
     */
    public static void userRemoveTraceMethod(String argusUser, ArgusMethod argusMethod) {

        if (userTraceMethods.isEmpty() || !userTraceMethods.containsKey(argusUser)) {
            return;
        }
        // 移除用户监听的方法
        userTraceMethods.get(argusUser).removeIf(method -> method.getMethod().equals(argusMethod.getMethod()));
        // 用户监听方法为空，则移除用户
        if (userTraceMethods.get(argusUser).isEmpty()) {
            userTraceMethods.remove(argusUser);
        }
    }

    /**
     * 移除用户监听方法
     *
     * @param argusUser 用户
     */
    public static void userRemoveAllMethod(String argusUser) {
        if (containsUser(argusUser)) {
            userMonitorMethods.remove(argusUser);
        }

        // 删除方法监听用户
        methodUsers.forEach((method, users) -> {
            users.removeIf(user -> user.equals(argusUser));
        });

    }

    public static void userRemoveAllTraceMethod (String argusUser) {
        if (userTraceMethods.isEmpty() || !userTraceMethods.containsKey(argusUser)) {
            return;
        }
        userTraceMethods.get(argusUser).clear();
    }

    /**
     * 根据pattern移除用户监听方法
     *
     * @param argusUser 用户
     * @param pattern pattern
     */
    public static void removeMonitorMethodWithPattern(String argusUser, String pattern) {

        userMonitorMethods.get(argusUser).removeIf(monitorInfo -> match(monitorInfo.getMethod().getUri(), pattern));

        if (userMonitorMethods.get(argusUser).isEmpty()) {
            userMonitorMethods.remove(argusUser);
            // 删除方法监听用户
            methodUsers.forEach((method, users) -> users.removeIf(user -> user.equals(argusUser)));
        }
    }

    /**
     * 移除过期的凭证
     */
    public static void clearExpiredToken() {
        userTokens.forEach((token, user) -> {
            if (user.getToken().getExpireTime() < System.currentTimeMillis()) {

                // 删除方法监听用户
                methodUsers.entrySet().removeIf(entry -> {
                    entry.getValue().removeIf(u -> u.equals(user.getToken().getToken()));
                    return entry.getValue().isEmpty();
                });

                // 删除用户监听方法
                userMonitorMethods.keySet().removeIf(u -> u.equals(user.getToken().getToken()));

                // 删除监听trace方法用户
                userTraceMethods.keySet().removeIf(u -> u.equals(user.getToken().getToken()));

                // 删除凭证
                userTokens.remove(token);
            }
        });
    }

    /**
     * 判断是否有凭证
     *
     * @param token token
     * @return 结果
     */
    public static boolean hasToken(String token) {
        if (Objects.isNull(token) || token.isEmpty()) {
            return false;
        }
        return userTokens.containsKey(token);
    }

    /**
     * 是否有uri
     *
     * @param uri uri
     * @return 结果
     */
    public static boolean hasUri(String uri) {
        return uriMethodCache.containsKey(uri);
    }

    /**
     * 获取接口方法
     *
     * @param uri 接口
     * @return 方法
     */
    public static ArgusMethod getUriMethod(String uri) {
        return uriMethodCache.get(uri);
    }

    /**
     * 根据方法获取用户列表
     *
     * @param method 方法
     * @return 用户列表
     */
    public static Map<String, MonitorInfo> getUsersByMethod(Method method) {

        Map<String, MonitorInfo> userMonitorMap = new HashMap<>();
        userMonitorMethods.forEach((user, methods) -> {
            Optional<MonitorInfo> first = methods.stream().filter(monitor -> monitor.getMethod().getMethod().equals(method)).findFirst();
            first.ifPresent(monitorInfo -> userMonitorMap.put(user, monitorInfo));
        });
        return userMonitorMap;
    }

    /**
     * 查询接口方法列表
     *
     * @param uri 接口路径
     * @return 方法列表
     */
    public static List<String> getUris(String uri) {

        return uriMethodCache.keySet().stream().filter(u -> u.contains(uri)).collect(Collectors.toList());
    }

    /**
     * 查询接口方法列表
     *
     * @param pattern 接口路径
     * @return 方法列表
     */
    public static List<String> getUrisWithPattern(String pattern) {

        return uriMethodCache.keySet().stream().filter(u -> match(u, pattern)).collect(Collectors.toList());
    }

    /**
     * 统计在线用户数
     *
     * @return 在线用户数
     */
    public static int countOnlineUser() {
        return userTokens.size();
    }

    /**
     * 查询用户监听接口列表
     *
     * @param argusUser 用户
     * @param pattern 接口
     * @return 接口列表
     */
    public static List<String> getUserMonitorUris(String argusUser, String pattern) {

        if (!containsUser(argusUser)) {
            return new ArrayList<>();
        }
        List<MonitorInfo> monitorInfos = userMonitorMethods.get(argusUser);
        if (Objects.isNull(pattern)) {
            return monitorInfos.stream().map(monitor -> monitor.getMethod().getUri()).sorted().collect(Collectors.toList());
        }
        return monitorInfos.stream().filter(monitor -> match(monitor.getMethod().getUri(), pattern)).map(monitor -> monitor.getMethod().getUri()).sorted().collect(Collectors.toList());
    }

    /**
     * 获取方法uri
     *
     * @param method 方法
     * @return uri
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
     * 移除用户token
     *
     * @param token token
     */
    public static void removeUserToken(String token) {
        userTokens.remove(token);
    }

    /**
     * 根据session获取用户
     *
     * @param session session
     * @return 用户
     */
    public static ArgusUser getUserBySession(WebSocketSession session) {
        for (Map.Entry<String, ArgusUser> entry : userTokens.entrySet()) {
            if (entry.getValue().getSession().equals(session)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 匹配路径
     * @param pattern 正则
     * @param uri 路径
     * @return 是否匹配
     */
    public static boolean match(String uri, String pattern) {
        if (!pattern.contains("*")) {
            return uri.equalsIgnoreCase(pattern);
        }
        return matchPattern(uri.toLowerCase(), pattern.toLowerCase());
    }

    /**
     * 匹配路径
     * @param pattern 正则
     * @param uri 路径
     * @return 是否匹配
     */
    public static boolean matchPattern(String uri, String pattern) {
        String regex = "^" + escapeAndReplaceWildcard(pattern) + "$";
        return Pattern.matches(regex, uri);
    }

    /**
     * escapeAndReplaceWildcard
     * @param pattern 正则
     * @return 正则
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

    /**
     * 用户是否已经监听了uri 调用链
     * @param token token
     * @param uri uri
     * @return 是否监听
     */
    public static boolean userHasTraceUri(String token, String uri) {
        if(userTraceMethods.isEmpty() || !userTraceMethods.containsKey(token)) {
            return false;
        }
        return userTraceMethods.get(token).stream().anyMatch(method -> method.getUri().equals(uri));
    }
}
