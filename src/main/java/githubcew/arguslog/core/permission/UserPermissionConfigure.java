package githubcew.arguslog.core.permission;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 用户自定义权限
 * @author chenenwei
 */
public interface UserPermissionConfigure {

    /**
     * 自定义角色列表
     * @return 角色
     */
    List<Role> roles ();

    /**
     * 自定义用户和角色绑定关系
     *      key: 角色名称
     *      values: 命令名称列表
     * @return 用户角色绑定关系
     */
    Map<String, Set<String>> userRoles();

    /**
     * 添加角色命令
     * @param roleCmdList 角色命令列表
     */
    void addRoleCommand(Map<String, Set<String>> roleCmdList);
}
