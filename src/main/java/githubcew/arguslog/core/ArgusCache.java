package githubcew.arguslog.core;

import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.auth.Token;
import githubcew.arguslog.core.method.ArgusMethod;
import githubcew.arguslog.core.method.MonitorInfo;

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
    private final static Map<ArgusMethod, List<ArgusUser>> methodUsers;

    /**
     * 用户监听方法列表
     */
    private final static Map<ArgusUser, List<MonitorInfo>> userMonitorMethods;

    /**
     * 用户列表
     */
    private final static List<ArgusUser> users;

    static {
        uriMethodCache = new ConcurrentHashMap<>(256);
        userMonitorMethods = new ConcurrentHashMap<>(1);
        methodUsers = new ConcurrentHashMap<>(1);
        users = new ArrayList<>(1);
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
    public static void addMethodUser (ArgusMethod method, ArgusUser user) {

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
    public static void addMonitorInfo (ArgusUser user, MonitorInfo monitorInfo) {
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
     * 添加方法监测用户
     * @param argusUser 用户
     */
    public static void addUser (ArgusUser argusUser) {
        if (users.stream().noneMatch(user -> user.getSession().equals(argusUser.getSession()))) {
            users.add(argusUser);
        }
    }

    /**
     * 更新用户监听方法
     * @param user 用户
     * @param monitorInfo 监控方法
     */
    public static void updateUserMethod (ArgusUser user, MonitorInfo monitorInfo) {
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
    public static boolean methodContainsUser (ArgusMethod argusMethod, ArgusUser argusUser) {
        if (!containsMethod(argusMethod)) {
            return false;
        }
        return methodUsers.get(argusMethod).stream().anyMatch(user -> argusUser.getSession().equals(user.getSession()));
    }

    /**
     * 用户是否包含某个方法
     * @param argusUser 用户
     * @param argusMethod 方法
     * @return 结果
     */
    public static boolean userContainsMethod (ArgusUser argusUser, ArgusMethod argusMethod) {

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
    public static boolean containsUser (ArgusUser argusUser) {
        if (userMonitorMethods.isEmpty()) {
            return false;
        }
        return userMonitorMethods.keySet().stream().anyMatch(user -> user.getSession().equals(argusUser.getSession()));
    }

    /**
     * 移除监听方法用户
     * @param argusMethod 方法
     * @param argusUser 用户
     */
    public static void methodRemoveUser (ArgusMethod argusMethod, ArgusUser argusUser) {
        if (!methodContainsUser(argusMethod, argusUser)) {
            return;
        }
        methodUsers.get(argusMethod).removeIf(user -> user.getSession().equals(argusUser.getSession()));

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
    public static void userRemoveMethod (ArgusUser argusUser, ArgusMethod argusMethod) {
        if (!userContainsMethod(argusUser, argusMethod)) {
            return;
        }
        userMonitorMethods.get(argusUser).removeIf(monitor -> monitor.getMethod().getMethod().equals(argusMethod.getMethod()));
        // 如果方法为空，则移除该用户
        if (userMonitorMethods.get(argusUser).isEmpty()) {
            userMonitorMethods.remove(argusUser);
        }
    }

    /**
     * 移除用户监听方法
     * @param argusUser 用户
     */
    public static void userRemoveAllMethod (ArgusUser argusUser) {
        if (containsUser(argusUser)) {
            userMonitorMethods.remove(argusUser);
        }

        // 删除方法监听用户
        methodUsers.forEach((method, users) -> {
            users.removeIf(user -> user.getSession().equals(argusUser.getSession()));
        });
    }

    /**
     * 移除凭证过期的用户
     */
    public static void clearExpiredUser () {
        methodUsers.forEach((method, users) -> {
            users.removeIf(user -> user.getToken().getExpireTime() < System.currentTimeMillis());
            if (users.isEmpty()) {
                methodUsers.remove(method);
            }
        });
    }

    /**
     * 判断是否有凭证
     * @param token token
     * @return 结果
     */
    public static boolean hasToken(Token token) {
        if (Objects.isNull(token) || Objects.isNull(token.getToken())|| token.getToken().isEmpty()) {
            return false;
        }
        return users.stream().anyMatch(user -> user.getToken().getToken().equals(token.getToken()));
    }

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
    public static Map<ArgusUser, MonitorInfo> getUsersByMethod (Method method) {

        Map<ArgusUser, MonitorInfo> userMonitorMap = new HashMap<>();
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

        return uriMethodCache.keySet().stream().filter(key -> key.contains(uri)).sorted().collect(Collectors.toList());
    }

    /**
     * 查询用户监听接口列表
     * @param argusUser 用户
     * @param uri 接口
     * @return
     */
    public static List<String> getUserMonitorUris (ArgusUser argusUser, String uri) {

        if (!containsUser(argusUser)) {
            return new ArrayList<>();
        }
        List<MonitorInfo> monitorInfos = userMonitorMethods.get(argusUser);
        if (Objects.isNull(uri)) {
            return monitorInfos.stream().map(monitor -> monitor.getMethod().getUri()).sorted().collect(Collectors.toList());
        }
        return monitorInfos.stream().filter(monitor -> monitor.getMethod().getUri().contains(uri)).map(monitor -> monitor.getMethod().getUri()).sorted().collect(Collectors.toList());
    }

}
