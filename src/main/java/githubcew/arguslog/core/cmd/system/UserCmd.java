package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.core.account.Account;
import githubcew.arguslog.core.account.ArgusUser;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import picocli.CommandLine;

import java.io.IOException;
import java.security.InvalidParameterException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 用户命令
 * @author chenenwei
 */
@CommandLine.Command(
        name = "user",
        description = "用户命令",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class UserCmd extends BaseCommand {

    @CommandLine.Parameters(
            index = "0",
            description = "操作类型 => list: 列出用户列表 add: 添加临时用户 remove: 移除临时用户 role: 角色列表",
            paramLabel = "list|add|remove|role"
    )
    private String operatorType;

    @CommandLine.Parameters(
            index = "1",
            arity = "0..1",
            description = "用户名"
    )
    private String username;

    @CommandLine.Parameters(
            index = "2",
            arity = "0..1",
            description = "密码",
            paramLabel = "password"
    )
    private String password;

    @CommandLine.Parameters(
            index = "3",
            arity = "0..*",
            description = "角色列表,仅operatorType 为 role操作时生效",
            paramLabel = "roles"
    )
    List<String> roleNames;

    @Override
    protected Integer execute() throws Exception {

        switch (operatorType) {
            case "list":
                listUser();
                break;
            case "add":
                addUser();
                break;
            case "remove":
                deleteUser();
                break;
            case "role":
                role();
                break;
            default:
                throw new InvalidParameterException("不支持的操作：" + operatorType);
        }
        return OK_CODE;
    }

    /**
     * 列出用户列表
     */
    private void listUser () {
        StringBuilder userInfo = new StringBuilder();

        userInfo.append(repeat(" ", 2))
                .append("用户")
                .append(repeat(" ", 10))
                .append("过期时间")
                .append(repeat(" ", 10))
                .append("角色列表")
                .append("\n")
                .append(repeat("─", 46))
                .append("\n");

        Set<String> allOnlineUser = ArgusCache.getAllOnlineUser();
        for (String username : allOnlineUser) {
            ArgusUser argusUser = ArgusCache.getUserByUsername(username);
            String expireTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(argusUser.getToken().getExpireTime()));
            userInfo.append(OutputWrapper.wrapperCopy(argusUser.getAccount().getUsername()))
                    .append("  ")
                    .append(expireTime)
                    .append("  ")
                    .append(argusUser.getAccount().getRoles())
                    .append("\n");
        }
        picocliOutput.out(userInfo.toString());
    }

    /**
     * 添加用户
     */
    private void addUser() {
        if(Objects.isNull(password) || password.length() < 6) {
            throw new InvalidParameterException("密码长度不能小于6");
        }
        if (ArgusCache.getTempUser(username) != null) {
            throw new InvalidParameterException("用户已存在");
        }
        ArgusCache.addTempUser(new Account(username, password));
    }

    /**
     * 删除用户
     */
    private void deleteUser() {
        if (Objects.isNull(username)) {
            throw new InvalidParameterException("请指定用户名");
        }
        // 移除临时用户
        ArgusCache.removeTempUser(username);

        // 移除用户监听数据
        ArgusCache.clearUserToken(username);

        // 移除用户登录信息
        ArgusUser argusUser = ArgusCache.getUserByUsername(username);
        if (!Objects.isNull(argusUser)) {
            ArgusCache.removeUserToken(argusUser.getToken().getToken());
            if(argusUser.getSession().isOpen()) {
                try {
                    argusUser.getSession().close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * 角色列表
     */
    private void role() {

        if (Objects.isNull(password) && Objects.isNull(roleNames)) {
            throw new InvalidParameterException("请输入角色名称");
        }
        if (Objects.isNull(roleNames)) {
            roleNames = new ArrayList<>();
        }
        // 如果是role操作，密码位置为角色的第一个，要加入到角色列表中
        roleNames.add(password);

        // 如果用户已登录在，则更新用户角色
        ArgusUser argusUser = ArgusCache.getUserByUsername(username);
        if (argusUser != null) {
            argusUser.getAccount().getRoles().addAll(new HashSet<>(roleNames));
        }

        Account tempUser = ArgusCache.getTempUser(username);
        if (!Objects.isNull(tempUser)) {
            tempUser.setRoles(new HashSet<>(roleNames));
        }
    }

    /**
     * 重复字符串
     *
     * @param str 要重复的字符串
     * @param count 重复次数
     * @return 重复后的字符串
     */
    private String repeat(String str, int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> str)
                .collect(Collectors.joining());
    }
}
