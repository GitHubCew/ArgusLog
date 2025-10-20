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
import githubcew.arguslog.core.cmd.monitor.RemoveCmd;
import githubcew.arguslog.core.cmd.mq.MqCmd;
import githubcew.arguslog.core.cmd.sql.SqlCmd;
import githubcew.arguslog.core.cmd.system.*;
import githubcew.arguslog.core.cmd.trace.RevertCmd;
import githubcew.arguslog.core.cmd.trace.TraceCmd;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 权限配置
 * @author chenenwei
 */
public class ArgusPermissionConfigure {

    private final List<Role> ROLES = Collections.synchronizedList(new ArrayList<>());

    private final Map<String, Set<String> > USER_ROLES = Collections.synchronizedMap(new HashMap<>());

    /**
     * 初始化方法
     */
    public void init() {

        // 添加系统角色
        ROLES.add(admin());
        ROLES.add(user());
        ROLES.add(coder());

        ArgusProperties argusProperties = ContextUtil.getBean(ArgusProperties.class);

        // 添加系统用户和角色绑定关系
        HashSet<String> adminRoles = new HashSet<>();
        adminRoles.add("admin");
        USER_ROLES.put(argusProperties.getUsername(), adminRoles);

        // 添加用户自定义角色
        try {
            UserPermissionConfigure userPermissionConfigure = ContextUtil.getBean(UserPermissionConfigure.class);
            // 添加自定义角色
            List<Role> roleList = userPermissionConfigure.roles();
            roleList.removeIf(r -> ROLES.stream().map(Role::getName).collect(Collectors.toList()).contains(r.getName()));
            ROLES.addAll(roleList);

            // 添加自定义角色用户
            Map<String, Set<String>> userRoles = userPermissionConfigure.userRoles();
            userRoles.forEach((username, roles) -> {
                if (!USER_ROLES.containsKey(username)) {
                    USER_ROLES.put(username, new HashSet<>());
                }
                USER_ROLES.get(username).addAll(roles);
            });

            // 添加用户角色命令
            Map<String, Set<String>> roleCommand = new HashMap<>();
            userPermissionConfigure.addRoleCommand(roleCommand);
            if (roleCommand.size() > 0) {
                roleCommand.forEach((roleName, cmdList) -> {
                    ROLES.stream().filter(role -> role.getName().equals(roleName)).findFirst().ifPresent(role -> role.getCmdList().addAll(cmdList));
                });
            }
        } catch (Exception e) {
            // 忽略
        }
    }

    /**
     * 查询用户权限
     * @param username 用户名
     * @return 用户角色
     */
    public Set<String> getUserRoles(String username) {
        if (!USER_ROLES.containsKey(username)) {
            return new HashSet<>(Collections.singletonList("user"));
        }
        return USER_ROLES.get(username);
    }

    /**
     * 获取用户可执行命令
     * @param username 用户名
     * @return 用户可执行命令
     */
    public Set<String> getUserCommands (String username) {
        Set<String> userRoles = getUserRoles(username);
        return ROLES.stream()
                .filter(role -> userRoles.contains(role.getName()))
                .map(Role::getCmdList)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    /**
     * 增加用户角色
     * @param username 用户名
     * @param roleName 角色名称
     */
    public void addUserRole(String username, String roleName) {
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
        if (!USER_ROLES.containsKey(username)) {
            return;
        }
        USER_ROLES.get(username).remove(roleName);
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
     * 判断是否有权限
     * @param username 用户名
     * @param cmd 命令
     * @return 是否有权限
     */
    public boolean hasPermission (String username, String cmd) {
        return getUserCommands(username).contains(cmd);
    }

    /**
     * 系统管理员角色
     * @return 管理员角色
     */
    private Role admin () {
        ArgusManager argusManager = ContextUtil.getBean(ArgusManager.class);
        Map<String, Class<? extends BaseCommand>> commands = argusManager.getCommandManager().getCommands();
        return new Role("admin", new HashSet<>(commands.keySet()));
    }

    /**
     * 系统用户角色
     * @return 用户角色
     */
    private Role user () {
        Role user = new Role();
        user.setName("user");
        user.setCmdList(baseCmd());
        return user;
    }

    private Role coder() {
        Role coder = new Role();
        coder.setName("coder");
        Set<String> cmdList = baseCmd();
        cmdList.addAll(Arrays.asList(
                new TraceCmd().getCmd(),
                new RevertCmd().getCmd(),
                new FindCmd().getCmd(),
                new InvokeCmd().getCmd(),
                new JadCmd().getCmd(),
                new SqlCmd().getCmd(),
                new MqCmd().getCmd(),
                new RedisCmd().getCmd(),
                new ResetCmd().getCmd()
        ));
        return coder;
    }

    /**
     * 基础命令
     * @return 基础命令
     */
    private Set<String> baseCmd () {

        return new HashSet<>(Arrays.asList(
                new ClearCmd().getCmd(),
                new ConnectCmd().getCmd(),
                new ExitCmd().getCmd(),
                new HelpCmd().getCmd(),
                new LogoutCmd().getCmd(),
                new ShowCmd().getCmd(),
                new LsCmd().getCmd(),
                new RemoveCmd().getCmd()
        ));
    }
}
