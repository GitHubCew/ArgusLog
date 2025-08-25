package githubcew.arguslog.core.cache;

import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.monitor.ArgusMethod;
import githubcew.arguslog.monitor.MonitorInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.web.socket.WebSocketSession;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Argus cache
 * @author chenenwei
 */
public class ArgusCache {

    /**
     * 接口方法缓存
     */
    private final static Map<String, ArgusMethod> uriMethodCache;

    /**
     * 监听方法的用户列表
     */
    private final static Map<ArgusMethod, List<String>> methodUsers;

    /**
     * 用户监听方法列表
     */
    private final static Map<String, List<MonitorInfo>> userMonitorMethods;

    /**
     * 用户tokens
     */
    private final static Map<String, ArgusUser> userTokens;

    static {
        uriMethodCache = new ConcurrentHashMap<>(256);
        userMonitorMethods = new ConcurrentHashMap<>(1);
        methodUsers = new ConcurrentHashMap<>(1);
        userTokens = new ConcurrentHashMap<>(1);
    }

    /**
     * 添加接口方法
     * @param uri 接口uri
     * @param method 方法
     */
    public static void addUriMethod (String uri, ArgusMethod method) {
        uriMethodCache.put(uri, method);
    }

    /**
     * 增加方法监测用户
     * @param method 方法
     * @param user 用户
     */
    public static void addMethodUser (ArgusMethod method, String user) {

        if (!containsMethod(method)) {
            methodUsers.put(method, new ArrayList<>(10));
        }
        if (!methodContainsUser(method, user)) {
            methodUsers.get(method).add(user);
        }
    }

    /**
     * 添加用户监测方法
     * @param user 用户
     * @param monitorInfo 监测信息
     */
    public static void addMonitorInfo (String user, MonitorInfo monitorInfo) {
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
     */
    public static void addAllMonitorInfo (String user, MonitorInfo monitorInfo) {
        if (!containsUser(user)) {
            userMonitorMethods.put(user, new ArrayList<>(uriMethodCache.size()));
        }
        uriMethodCache.forEach((uri, method) -> {
            if (!methodUsers.containsKey(method)) {
                methodUsers.put(method, new ArrayList<>(1));
            }
            methodUsers.get(method).add(user);

            List<MonitorInfo> monitorInfos = userMonitorMethods.get(user);
            MonitorInfo monitor;
            if (!userContainsMethod(user, method)) {
                monitor = new MonitorInfo();
                BeanUtils.copyProperties(monitorInfo, monitor);
                monitor.setMethod(method);
                monitorInfos.add(monitor);
            }
        });
    }

    /**
     * 添加用户凭证
     * @param argusUser 用户
     */
    public static void addUserToken(String token, ArgusUser argusUser) {
        userTokens.put(token, argusUser);
    }

    public static ArgusUser getUserToken(String token) {
        return userTokens.get(token);
    }

    /**
     * 更新用户监听方法
     * @param user 用户
     * @param monitorInfo 监控方法
     */
    public static void updateUserMethod (String user, MonitorInfo monitorInfo) {
        ListIterator<MonitorInfo> monitorInfoListIterator = userMonitorMethods.get(user).listIterator();
        while (monitorInfoListIterator.hasNext()) {
            MonitorInfo monitor = monitorInfoListIterator.next();
            if (monitor.getMethod().equals(monitorInfo.getMethod())) {
                monitorInfoListIterator.set(monitorInfo);
            }
        }
    }

    /**
     * 是否包含用户
     * @param argusUser 用户
     * @return 结果
     */
    public static boolean methodContainsUser (ArgusMethod argusMethod, String argusUser) {
        if (!containsMethod(argusMethod)) {
            return false;
        }
        return methodUsers.get(argusMethod).stream().anyMatch(user -> user.equals(argusUser));
    }

    /**
     * 用户是否包含某个方法
     * @param argusUser 用户
     * @param argusMethod 方法
     * @return 结果
     */
    public static boolean userContainsMethod (String argusUser, ArgusMethod argusMethod) {

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
     * @param argusMethod 方法
     * @return 结果
     */
    public static boolean containsMethod (ArgusMethod argusMethod) {
        if (methodUsers.isEmpty()) {
            return false;
        }
        return methodUsers.keySet().stream().anyMatch(method -> method.getMethod().equals(argusMethod.getMethod()));
    }

    /**
     * 是否包含方法
     * @param method 方法
     * @return 结果
     */
    public static boolean containsMethod (Method method) {
        if (methodUsers.isEmpty()) {
            return false;
        }
        return methodUsers.keySet().stream().anyMatch(argusMethod -> method.equals(argusMethod.getMethod()));
    }

    /**
     * 是否包含用户
     * @param argusUser 用户
     * @return 结果
     */
    public static boolean containsUser (String argusUser) {
        if (userMonitorMethods.isEmpty()) {
            return false;
        }
        return userMonitorMethods.keySet().stream().anyMatch(user -> user.equals(argusUser));
    }

    /**
     * 移除监听方法用户
     * @param argusMethod 方法
     * @param argusUser 用户
     */
    public static void methodRemoveUser (ArgusMethod argusMethod, String argusUser) {
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
     * @param argusUser 用户
     * @param argusMethod 方法
     */
    public static void userRemoveMethod (String argusUser, ArgusMethod argusMethod) {
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
     * @param argusUser 用户
     */
    public static void userRemoveAllMethod (String argusUser) {
        if (containsUser(argusUser)) {
            userMonitorMethods.remove(argusUser);
        }

        // 删除方法监听用户
        methodUsers.forEach((method, users) -> {
            users.removeIf(user -> user.equals(argusUser));
        });

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

                // 删除凭证
                userTokens.remove(token);
            }
        });
    }

    /**
     * 判断是否有凭证
     * @param token token
     * @return 结果
     */
    public static boolean hasToken(String token) {
        if (Objects.isNull(token)|| token.isEmpty()) {
            return false;
        }
        return userTokens.containsKey(token);
    }

    /**
     * 是否有uri
     * @param uri uri
     * @return
     */
    public static boolean hasUri (String uri) {
        return uriMethodCache.containsKey(uri);
    }

    /**
     * 获取接口方法
     * @param uri 接口
     * @return 方法
     */
    public static ArgusMethod getUriMethod (String uri) {
        return uriMethodCache.get(uri);
    }

    /**
     * 根据方法获取用户列表
     * @param method 方法
     * @return 用户列表
     */
    public static Map<String, MonitorInfo> getUsersByMethod (Method method) {

        Map<String, MonitorInfo> userMonitorMap = new HashMap<>();
        userMonitorMethods.forEach((user, methods) -> {
            Optional<MonitorInfo> first = methods.stream().filter(monitor -> monitor.getMethod().getMethod().equals(method)).findFirst();
            first.ifPresent(monitorInfo -> userMonitorMap.put(user, monitorInfo));
       });
       return userMonitorMap;
    }

    /**
     * 查询接口方法列表
     * @param uri 接口路径
     * @return 方法列表
     */
    public static List<String> getUris(String uri) {

        return uriMethodCache.keySet().stream().filter(u -> u.contains(uri)).collect(Collectors.toList());
    }

    /**
     * 统计在线用户数
     * @return 在线用户数
     */
    public static int countOnlineUser () {
        return userTokens.size();
    }

    /**
     * 查询用户监听接口列表
     * @param argusUser 用户
     * @param uri 接口
     * @return
     */
    public static List<String> getUserMonitorUris (String argusUser, String uri) {

        if (!containsUser(argusUser)) {
            return new ArrayList<>();
        }
        List<MonitorInfo> monitorInfos = userMonitorMethods.get(argusUser);
        if (Objects.isNull(uri)) {
            return monitorInfos.stream().map(monitor -> monitor.getMethod().getUri()).sorted().collect(Collectors.toList());
        }
        return monitorInfos.stream().filter(monitor -> monitor.getMethod().getUri().contains(uri)).map(monitor -> monitor.getMethod().getUri()).sorted().collect(Collectors.toList());
    }

    /**
     * 获取方法uri
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

    public static void removeUserToken(String id) {
        userTokens.remove(id);
    }

    public static ArgusUser getUserBySession (WebSocketSession session) {
        for (Map.Entry<String, ArgusUser> entry : userTokens.entrySet()) {
            if (entry.getValue().getSession().equals(session)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
