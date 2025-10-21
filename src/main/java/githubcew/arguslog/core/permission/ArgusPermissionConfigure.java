package githubcew.arguslog.core.permission;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.ArgusManager;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.core.cmd.cache.RedisCmd;
import githubcew.arguslog.core.cmd.code.FindCmd;
import githubcew.arguslog.core.cmd.code.InvokeCmd;
import githubcew.arguslog.core.cmd.code.JadCmd;
import githubcew.arguslog.core.cmd.monitor.LsCmd;
import githubcew.arguslog.core.cmd.monitor.MonitorCmd;
import githubcew.arguslog.core.cmd.monitor.RemoveCmd;
import githubcew.arguslog.core.cmd.mq.MqCmd;
import githubcew.arguslog.core.cmd.sql.SqlCmd;
import githubcew.arguslog.core.cmd.system.*;
import githubcew.arguslog.core.cmd.trace.RevertCmd;
import githubcew.arguslog.core.cmd.trace.TraceCmd;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限配置
 * @author chenenwei
 */
public class ArgusPermissionConfigure {

    private static final Logger log = LoggerFactory.getLogger(ArgusPermissionConfigure.class);

    /**
     * 角色列表
     */
    private final List<Role> ROLES = Collections.synchronizedList(new ArrayList<>());

    /**
     * 用户关联角色map
     */
    private final Map<String, Set<String> > USER_ROLES = Collections.synchronizedMap(new HashMap<>());

    /**
     * 系统默认角色
     */
    private final static List<String> SYSTEM_ROLES = Arrays.asList(
            Role.Type.ADMIN.getName(),
            Role.Type.USER.getName(),
            Role.Type.CODER.getName(),
            Role.Type.TESTER.getName()
    );

    /**
     * 初始化方法
     */
    public void init() {

        // 添加系统角色
        ROLES.add(admin());
        ROLES.add(user());
        ROLES.add(coder());
        ROLES.add(tester());

        ArgusProperties argusProperties = ContextUtil.getBean(ArgusProperties.class);

        // 添加系统用户和角色绑定关系
        HashSet<String> adminRoles = new HashSet<>();
        adminRoles.add(Role.Type.ADMIN.getName());
        USER_ROLES.put(argusProperties.getUsername().toLowerCase(), adminRoles);

        // 添加用户自定义角色
        try {
            UserPermissionConfigure userPermissionConfigure = ContextUtil.getBean(UserPermissionConfigure.class);

            // 添加自定义角色
            List<Role> roleList = userPermissionConfigure.roles();
            if (roleList != null && !roleList.isEmpty()) {
                roleList = new ArrayList<>(roleList);

                // 提前缓存已存在角色名，减少重复 stream
                Set<String> existingRoleNames = ROLES.stream()
                        .map(Role::getName)
                        .collect(Collectors.toSet());

                roleList.removeIf(r -> existingRoleNames.contains(r.getName()));
                ROLES.addAll(roleList);
            }

            // 添加自定义用户角色
            Map<String, Set<String>> userRoles = userPermissionConfigure.userRoles();
            if (userRoles != null) {
                userRoles.forEach((username, roles) -> {
                    username = username.toLowerCase();
                    if (USER_ROLES.containsKey(username)) {
                        USER_ROLES.get(username).addAll(roles);
                    }
                    else {
                        USER_ROLES.put(username, roles);
                    }
                });
            }

            // 用户添加角色
            Map<String, Set<String>> map = new HashMap<>();
            userPermissionConfigure.addUserRoles(map);
            map.forEach((username, roles) -> {
                username = username.toLowerCase();
                if (USER_ROLES.containsKey(username)) {
                    USER_ROLES.get(username).addAll(roles);
                }
                else {
                    USER_ROLES.put(username, roles);
                }
            });

            // 添加用户角色命令
            Map<String, Set<String>> roleCommand = new HashMap<>();
            userPermissionConfigure.addRoleCommand(roleCommand);
            if (!roleCommand.isEmpty()) {
                roleCommand.forEach((roleName, cmdList) -> {
                    ROLES.stream()
                            .filter(role -> role.getName().equals(roleName))
                            .findFirst()
                            .ifPresent(role -> role.getCmdList().addAll(cmdList));
                });
            }

        } catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.error("用户权限配置异常", e);
            }
        }
    }

    /**
     * 查询角色列表
     * @return 角色
     */
    public Map<String, Set<String>> getRoles() {
        HashMap<String, Set<String> > rolesMap = new HashMap<>();
        ROLES.forEach(role -> rolesMap.put(role.getName(), role.getCmdList()));
        return rolesMap;
    }

    /**
     * 查询用户角色列表
     * @param username 用户名
     * @return 用户角色
     */
    public Set<String> getUserRoles(String username) {
        username = username.toLowerCase();
        if (!USER_ROLES.containsKey(username)) {

            return defaultRole();
        }
        return USER_ROLES.get(username);
    }

    /**
     * 获取用户可执行命令
     * @param username 用户名
     * @return 用户可执行命令
     */
    public Set<String> getUserCommands (String username) {
        username = username.toLowerCase();
        Set<String> userRoles = getUserRoles(username);
        return ROLES.stream()
                .filter(role -> userRoles.contains(role.getName()))
                .map(Role::getCmdList)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    /**
     * 获取角色可执行命令
     * @param roleNames 角色名
     * @return 用户可执行命令
     */
    public Set<String> getRoleCommands (Set<String> roleNames) {
        if (roleNames == null) {
            return null;
        }

        Set<Role> set = ROLES.stream().filter(role -> roleNames.contains(role.getName())).collect(Collectors.toSet());

        if (set.size() == 0) {
            return null;
        }

        return set.stream().map(Role::getCmdList).flatMap(Collection::stream).collect(Collectors.toSet());
    }

    /**
     * 增加用户角色
     * @param username 用户名
     * @param roleName 角色名称
     */
    public void addUserRole(String username, String roleName) {
        username = username.toLowerCase();
        if (!USER_ROLES.containsKey(username)) {
            USER_ROLES.put(username, new HashSet<>());
        }
        if (USER_ROLES.get(username).contains(roleName)) {
            return;
        }
        USER_ROLES.get(username).add(roleName);
    }

    /**
     * 删除用户角色
     * @param username 用户名
     * @param roleName 角色名称
     */
    public void removeUserRole(String username, String roleName) {
        username = username.toLowerCase();
        if (!USER_ROLES.containsKey(username)) {
            return;
        }
        USER_ROLES.get(username).remove(roleName);
    }

    /**
     * 判断角色是否存在
     * @param roleName 角色名称
     * @return 是否存在
     */
    public boolean hasRole(String roleName) {
        return ROLES.stream().map(Role::getName).collect(Collectors.toSet()).contains(roleName);
    }

    /**
     * 增加用户角色
     * @param role 角色
     */
    public void addRole(Role role) {
        if (ROLES.contains(role)) {
            return;
        }
        ROLES.add(role);
    }

    /**
     * 移除角色
     * @param roleName 角色名称
     */
    public void removeRole(String roleName) {
        if (SYSTEM_ROLES.contains(roleName)) {
            return;
        }
        ROLES.removeIf(r -> r.getName().equals(roleName));
    }

    /**
     * 是否是系统角色
     * @param roleName 角色名称
     * @return 是否是系统角色
     */
    public boolean isSystemRole(String roleName) {
        return SYSTEM_ROLES.contains(roleName);
    }

    /**
     * 判断是否有权限
     * @param username 用户名
     * @param cmd 命令
     * @return 是否有权限
     */
    public boolean hasPermission (String username, String cmd) {

        return getUserCommands(username).contains(cmd);
    }

    /**
     * 增加用户角色
     * @param username 用户名
     * @param role 角色
     */
    public void addUserRole (String username, Role role) {
        username = username.toLowerCase();
        if (!USER_ROLES.containsKey(username)) {
            USER_ROLES.put(username, new HashSet<>());
        }
        USER_ROLES.get(username).add(role.getName());
    }

    /**
     * 移除用户角色
     * @param username 用户名
     * @param role 角色
     */
    public void removeUserRole (String username, Role role) {
        username = username.toLowerCase();
        if (!USER_ROLES.containsKey(username)) {
            return;
        }
        USER_ROLES.get(username).remove(role.getName());
    }

    /**
     * 系统管理员角色
     * @return 管理员角色
     */
    private Role admin () {
        ArgusManager argusManager = ContextUtil.getBean(ArgusManager.class);
        Map<String, Class<? extends BaseCommand>> commands = argusManager.getCommandManager().getCommands();
        return new Role(Role.Type.ADMIN.getName(), new HashSet<>(commands.keySet()));
    }

    /**
     * 系统用户角色
     * @return 用户角色
     */
    private Role user () {
        Role user = new Role();
        user.setName(Role.Type.USER.getName());
        user.setCmdList(base());
        return user;
    }

    /**
     * 程序员角色
     * @return 程序员角色
     */
    private Role coder() {
        Role coder = new Role();
        coder.setName(Role.Type.CODER.getName());
        // 基础权限
        Set<String> cmdList = new HashSet<>();
        // 开发者权限
        cmdList.addAll(base());
        cmdList.addAll(monitor());
        cmdList.addAll(trace());
        cmdList.addAll(cache());
        cmdList.addAll(code());
        cmdList.addAll(sql());
        cmdList.addAll(mq());

        coder.setCmdList(cmdList);
        return coder;
    }

    /**
     * 测试角色
     * @return 测试角色
     */
    private Role tester() {
        Role coder = new Role();
        coder.setName(Role.Type.TESTER.getName());
        // 基础权限
        Set<String> cmdList = new HashSet<>();
        // 测试人员权限
        cmdList.addAll(base());
        cmdList.addAll(monitor());
        cmdList.addAll(trace());
        coder.setCmdList(cmdList);
        return coder;
    }

    /**
     * 默认角色
     * @return 默认角色
     */
    private Set<String> defaultRole () {
        Set<String> defaultRole = new HashSet<>();
        defaultRole.add(Role.Type.USER.getName());
        return defaultRole;
    }

    private Set<String> base() {

        return new HashSet<>(Arrays.asList(
                new ConnectCmd().getCmd(),
                new ExitCmd().getCmd(),
                new HelpCmd().getCmd(),
                new LogoutCmd().getCmd(),
                new ShowCmd().getCmd(),
                new RmUserCmd().getCmd(),
                new ResetCmd().getCmd()
        ));
    }

    private Set<String> monitor () {
        return new HashSet<>(Arrays.asList(
                new LsCmd().getCmd(),
                new MonitorCmd().getCmd(),
                new RemoveCmd().getCmd()
        ));
    }

    private Set<String> trace () {
        return new HashSet<>(Arrays.asList(
                new TraceCmd().getCmd(),
                new RevertCmd().getCmd()
        ));
    }

    private Set<String> sql () {
        return new HashSet<>(Arrays.asList(
                new SqlCmd().getCmd()
        ));
    }

    private Set<String> mq () {
        return new HashSet<>(Arrays.asList(
                new MqCmd().getCmd()
        ));
    }

    private Set<String> cache () {
        return new HashSet<>(Arrays.asList(
                new RedisCmd().getCmd()
        ));
    }

    private Set<String> code () {
        return new HashSet<>(Arrays.asList(
                new JadCmd().getCmd(),
                new FindCmd().getCmd(),
                new InvokeCmd().getCmd()
        ));
    }

}
