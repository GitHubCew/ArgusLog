package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.core.permission.ArgusPermissionConfigure;
import githubcew.arguslog.core.permission.Role;
import picocli.CommandLine;

import java.util.Objects;
import java.util.Set;

/**
 * 角色命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "role",
        description = "角色命令",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class RoleCmd extends BaseCommand {

    @CommandLine.Parameters(
            index = "0",
            description = "操作类型operatorType: list(查询角色列表) add(增加角色) remove (移除角色)",
            arity = "1",
            paramLabel = "list|add|remove"
    )
    private String operatorType;

    @CommandLine.Parameters(
            index = "1",
            description = "角色名称",
            paramLabel = "roleName",
            arity = "0..1"
    )
    private String roleName;

    @CommandLine.Parameters(
            index = "2",
            description = "命令列表",
            paramLabel = "cmdList",
            arity = "0..*"
    )
    private Set<String> cmdList;

    @Override
    protected Integer execute() throws Exception {

        switch (operatorType) {
            case "list":
                listRole();
                break;
            case "add":
                addRole();
                break;
            case "remove":
                removeRole();
                break;
            default:
                throw new RuntimeException("不支持的操作类型：" + operatorType);
        }

        return OK_CODE;
    }

    private void listRole () {
        ArgusPermissionConfigure argusPermissionConfigure = ContextUtil.getBean(ArgusPermissionConfigure.class);
        System.out.println("角色列表：");

        picocliOutput.out("角色名称          命令列表");
        picocliOutput.out("——————————————————————————————");
        argusPermissionConfigure.getRoles().forEach((role, cmdList) -> {
            picocliOutput.out(role + " => " + cmdList);
        });
    }

    /**
     * 增加角色
     */
    private void addRole () {

        if (Objects.isNull(roleName)) {
            throw new RuntimeException("请输入角色名称");
        }

        if (Objects.isNull(cmdList) || cmdList.isEmpty()) {
            throw new RuntimeException("请输入命令列表");
        }

        ArgusPermissionConfigure argusPermissionConfigure = ContextUtil.getBean(ArgusPermissionConfigure.class);
        if (argusPermissionConfigure.hasRole(roleName)) {
            throw new RuntimeException("角色已存在");
        }

        argusPermissionConfigure.addRole(new Role(roleName, cmdList));
    }

    /**
     * 移除角色
     */
    private void removeRole () {

        if (Objects.isNull(roleName)) {
            throw new RuntimeException("请输入角色名称");
        }

        ArgusPermissionConfigure argusPermissionConfigure = ContextUtil.getBean(ArgusPermissionConfigure.class);
        if (!argusPermissionConfigure.hasRole(roleName)) {
            throw new RuntimeException("角色不存在");
        }

        if (argusPermissionConfigure.isSystemRole(roleName)){
            throw new RuntimeException("系统角色不能删除");
        }
        argusPermissionConfigure.removeRole(roleName);
    }
}
