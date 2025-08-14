package githubcew.arguslog.core;

import githubcew.arguslog.business.auth.ArgusUser;
import githubcew.arguslog.business.socket.SessionContext;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 缓存
 * @author  chenenwei
 */
public class Cache {

    /**
     * 方法缓存
     */
    private final static Map<String, Method> methodCache;

    /**
     * 方法-用户缓存
     */
    private final static Map<Method, List<String>> methodUsers;

    /**
     * 用户-方法缓存
     */
    private final static Map<String, List<MonitorInfo>> userMethods;

    /**
     * 用户凭证
     */
    private final static Map<String, ArgusUser> userCredentials;

    static {
        methodCache = new ConcurrentHashMap<>(256);
        userMethods = new ConcurrentHashMap<>(1);
        methodUsers = new ConcurrentHashMap<>(1);
        userCredentials = new ConcurrentHashMap<>(1);
    }

    /**
     * 添加方法缓存
     * @param uri  uri
     * @param method 方法
     */
    public static void addMethodCache (String uri, Method method) {
        methodCache.put(uri, method);
    }

    /**
     * 获取方法uri
     * @return 方法uri
     */
    public static List<String> getUris () {
        return new ArrayList<>(methodCache.keySet());
    }


    /**
     * 获取方法
     * @param uri uri
     * @return 方法
     */
    public static Method getMethod (String uri) {
        return methodCache.get(uri);
    }



    /**
     * 新增用户
     * @param method 方法
     * @param user 用户
     */
    public static void addUser (Method method, String user) {
        // 处理methodCache
        if(!methodUsers.containsKey(method)) {
            List<String> users = new ArrayList<>();
            users.add(user);
            methodUsers.put(method, users);
        }
        else {
            methodUsers.get(method).add(user);
        }

        // 处理userMethods
        if(!userMethods.containsKey(user)) {
            List<MonitorInfo> methods = new ArrayList<>();
            MonitorInfo monitorInfo = new MonitorInfo(method, true, false, false, false);
            monitorInfo.setMethod(method);
            methods.add(monitorInfo);
            userMethods.put(user, methods);
        }
        else {
            List<MonitorInfo> methods = userMethods.get(user);
            MonitorInfo monitorInfo = new MonitorInfo();
            monitorInfo.setMethod(method);
            methods.add(monitorInfo);
        }
    }

    /**
     * 移除用户
     * @param method 方法
     * @param user 用户
     */
    public static void removeUser (Method method, String user) {
        if(methodUsers.containsKey(method)) {
            List<String> users = methodUsers.get(method);
            users.remove(user);
            if(users.isEmpty()) {
                methodUsers.remove(method);
            }
        }
    }

    /**
     * 是否包含用户
     * @param method 方法
     * @param user 用户
     * @return 是否包含
     */
    public static boolean containsUser (Method method, String user) {
        if(methodUsers.containsKey(method)) {
            return methodUsers.get(method).contains(user);
        }
        return false;
    }

    /**
     * 是否包含某个用户
     * @param user 用户
     * @return 是否包含
     */
    public static boolean hasUser (String user) {
        return userMethods.containsKey(user);
    }

    /**
     * 清除用户方法
     * @param user 用户
     */
    public static void clearUser (String user) {
        if(userMethods.containsKey(user)) {
            userMethods.remove(user);
            methodUsers.forEach((k,v) -> {v.remove(user);
            if (v.isEmpty()) {
                methodUsers.remove(k);
                }
            });
        }
    }

    /**
     * 增加方法
     * @param user 用户
     * @param monitor 监控信息
     */
    public static void addMethod (String user, MonitorInfo monitor) {
        // 处理userMethods
        if(!userMethods.containsKey(user)) {
            List<MonitorInfo> methods = new ArrayList<>();
            methods.add(monitor);
            userMethods.put(user, methods);
        }
        else {
            userMethods.get(user).add(monitor);
        }
        // 处理methodCache
        if (methodUsers.containsKey(monitor.getMethod())) {
            methodUsers.get(monitor.getMethod()).add(user);
        } else {
            List<String> users = new ArrayList<>();
            users.add(user);
            methodUsers.put(monitor.getMethod(), users);
        }
    }


    /**
     * 移除用户方法
     * @param user 用户
     * @param method 方法
     */
    public static void removeMethod (String user, Method method) {
        if(userMethods.containsKey(user)) {
            List<MonitorInfo> monitors = userMethods.get(user);
            monitors.removeIf(m -> m.getMethod().equals(method));
            if(monitors.isEmpty()) {
                userMethods.remove(user);
            }
        }
    }

    /**
     * 是否包含方法
     * @param user 用户
     * @param method 方法
     * @return 是否包含
     */
    public static boolean containsMethod (String user, Method method) {
        if(userMethods.containsKey(user)) {
            return userMethods.get(user).contains(method);
        }
        return false;
    }

    /**
     * 是否包含某个方法
     * @param method 方法
     * @return 是否包含
     */
    public static boolean hasMethod (Method method) {
        return methodUsers.containsKey(method);
    }

    /**
     * 清除方法
     * @param method 方法
     * @return 是否成功
     */
    public static boolean clearMethod (Method method) {
        if(methodUsers.containsKey(method)) {
            methodUsers.remove(method);
            return true;
        }
        return false;
    }

    /**
     * 是否包含uri
     * @param uri uri
     * @return 是否包含
     */
    public static boolean hasUri (String uri) {
        return methodCache.containsKey(uri);
    }

    /**
     * 获取某个方法的所有用户
     * @param method  方法
     * @return 所有用户
     */
    public static Map<String, MonitorInfo> getUsersByMethod (Method method) {
        if(!methodUsers.containsKey(method)) {
            return Collections.emptyMap();
        }
        Map<String, MonitorInfo> result = new HashMap<>();
        List<String> users = methodUsers.get(method);
        users.forEach(user -> {
            List<MonitorInfo> monitors = userMethods.get(user);
            if (Objects.isNull(monitors)) {
                return;
            }
            monitors.forEach(monitor -> {
                if(monitor.getMethod().equals(method)) {
                    result.put(user, monitor);
                }
            });
        });
        return result;
    }

    public static List<String> getUrisByUser (String user) {
        List<String> uris = new ArrayList<>();
        if(!userMethods.containsKey(user)) {
            return Collections.emptyList();
        }
        List<Method> methods = userMethods.get(user).stream().map(MonitorInfo::getMethod).collect(Collectors.toList());
        methodCache.forEach((uri, method) -> {
            if(methods.contains(method)) {
                uris.add(uri);
            }
        });
        return uris;
    }

    /**
     * 是否有凭证
     * @param credentials 凭证
     * @return 是否有凭证
     */
    public static boolean hasCredentials(String credentials) {
        return userCredentials.containsKey(credentials);
    }

    /**
     * 添加凭证
     * @param credentials 凭证
     * @param argusUser 用户
     */
    public static void addCredentials(String credentials, ArgusUser argusUser) {
       userCredentials.put(credentials, argusUser);
    }

    /**
     * 移除过期的凭证
     * @param currenTime 当前时间
     */
    public static void removeCredentials(Long currenTime) {
        userCredentials.forEach((cred, user) -> {
            if (currenTime - user.getExpireTime() > 0) {
                userCredentials.remove(cred);
            };
        });
    }
}
