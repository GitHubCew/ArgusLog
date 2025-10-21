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
     * @return 角色列表
     */
    default List<Role> roles () {return  null;};

    /**
     * 自定义用户和角色绑定关系
     *      key: 角色名称
     *      value: 命令名称列表
     * @return 用户角色绑定关系map
     */
    default Map<String, Set<String>> userRoles() {return null;};

    /**
     * 添加角色命令
     *   key: 角色名称
     *   value: 命令名称列表
     * @param roleCmdMap 角色命令map
     */
    default void addRoleCommand(Map<String, Set<String>> roleCmdMap){};

    /**
     * 添加用户角色
     *   key: 用户名
     *   value: 角色名称列表
     * @param userRoleMap 用户角色map
     */
    default void addUserRoles (Map<String, Set<String>> userRoleMap) {}
}
