package githubcew.arguslog.core.permission;

import lombok.Data;

import java.util.Set;

/**
 * 角色
 *
 * @author chenenwei
 */
@Data
public class Role {

    /**
     * 角色名称
     */
    private String name;

    /**
     * 命令列表
     */
    private Set<String> cmdList;

    public Role() {
    }

    public Role(String name, Set<String> cmdList) {
        this.name = name;
        this.cmdList = cmdList;
    }


    public static enum Type{

        ADMIN("admin"),

        USER("user"),

        CODER("coder"),

        TESTER("tester")
        ;

        private final String name;

        Type(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
