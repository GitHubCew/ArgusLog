package githubcew.arguslog.business.auth;

import githubcew.arguslog.business.cmd.*;
import githubcew.arguslog.core.Constant;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * argus认证管理
 * @author chenenwei
 */
public class ArgusAuthManager {

    /**
     * 用户名
     */
    private ArgusUser argusUser;

    /**
     * 不需要检验的命令
     */
    private List<String> excludeCmd;
    /**
     * 认证器
     */
    private List<Authenticator> authenticators;
    /**
     * 命令
     */
    private BaseCmd command;
    /**
     * 用户命令
     */
    private String sourceCmd;

    /**
     * 命令
     */
    private String cmd;

    /**
     * 参数
     */
    private String[] args;

    /**
     * 私有构造器
     */
    private ArgusAuthManager(Builder builder) {
        this.authenticators = builder.authenticators;
        this.excludeCmd = builder.excludeCmd;
        this.argusUser = new ArgusUser();
    }

    /**
     * 创建Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder
     */
    public static class Builder {

        /**
         * 认证器列表
         */
        private final List<Authenticator> authenticators = new ArrayList<>();

        /**
         * 不校验权限命令列表
         */
        private final List<String> excludeCmd = new ArrayList<>();

        /**
         *  添加认证器
         * @param authenticator 认证器
         * @return Builder
         */
        public Builder addAuthenticator(Authenticator authenticator) {
            this.authenticators.add(authenticator);
            return this;
        }

        /**
         * 添加认证器列表
         * @param authenticators 认证器列表
         * @return Builder
         */
        public Builder addAuthenticators(List<Authenticator> authenticators) {
            this.authenticators.addAll(authenticators);
            return this;
        }

        /**
         * 添加不校验权限命令
         * @param cmd 命令
         * @return Builder
         */
        public Builder addExcludeCmd(String cmd) {
            this.excludeCmd.add(cmd);
            return this;
        }

        /**
         * 添加不校验权限命令
         * @param cmd 命令
         * @return Builder
         */
        public Builder addExcludeCmd(List<String> cmd) {
            this.excludeCmd.addAll(cmd);
            return this;
        }

        /**
         * 创建ArgusAuthManager
         * @return ArgusAuthManager
         */
        public ArgusAuthManager build() {
            return new ArgusAuthManager(this);
        }
    }

    /**
     * 认证
     * @param sourceInput 输入
     * @return 认证结果
     */
    public static Object authenticateAndExecute(WebSocketSession session, String sourceInput) {

        List<String> excludeCmd = new ArrayList<>();
        return ArgusAuthManager.builder()
                .addExcludeCmd(excludeCmd)
                .addAuthenticators(Arrays.asList(new PasswordAuthenticator(), new TokenAuthenticator()))
                .build()
                .userSession(session)
                .extract(sourceInput)
                .authenticate()
                .choice()
                .execute();

    }

    /**
     * 用户信息
     * @param session 会话
     * @return ArgusAuthManager
     */
    public ArgusAuthManager userSession(WebSocketSession session) {
        this.argusUser.setSession(session);
        this.argusUser.setSessionId(session.getId());
        return this;
    }

    /**
     * 提取信息
     * @param sourceInput 输入
     * @return ArgusAuthManager
     */
    public ArgusAuthManager extract (String sourceInput) {

        if (sourceInput == null || sourceInput.isEmpty()) {
            return this;
        }

        // 提取token
        if (sourceInput.contains(Constant.TOKEN_SPLIT)) {
            String[] tokenParts = sourceInput.split(Constant.TOKEN_SPLIT);
            this.argusUser.setToken(tokenParts[0]);
            if (tokenParts.length > 1) {
                this.sourceCmd = tokenParts[1];
            }
        } else {
            this.sourceCmd = sourceInput;
        }

        // 提取用户和密码
        String[] userParts = sourceInput.split(Constant.SPACE_PATTERN);
        if ((userParts.length == 3 || userParts.length == 4) && userParts[0].equals("auth")) {
            this.argusUser.setUsername(userParts[1]);
            this.argusUser.setPassword(userParts[2]);
        }

        // 提取命令和参数
        String[] cmdParts = this.sourceCmd.split(Constant.SPACE_PATTERN);
        if (cmdParts.length > 0) {
            this.cmd = cmdParts[0];
            this.args = Arrays.copyOfRange(cmdParts, 1, cmdParts.length);
        }

        return this;
    }

    /**
     * 认证
     * @return ArgusAuthManager
     */
    public ArgusAuthManager authenticate() {

        if (excludeCmd.contains(this.cmd)) {
            return this;
        }
        boolean isAuthed = false;
        for (Authenticator authenticator : authenticators) {
            if (authenticator.authenticate(this.argusUser)) {
                isAuthed = true;
                break;
            }
        }
        if (!isAuthed) {
            throw new RuntimeException("unauthorized");
        }
        return this;
    }


    /**
     * 选择命令
     * @return ArgusAuthManager
     */
    public ArgusAuthManager choice() {

        switch (this.cmd) {
            case "auth":
                command = new Auth(this.cmd, this.args);
                break;
            case "lsm":
                command = new LsMonitor(this.cmd, this.args);
                break;
            case "ls":
                command = new Ls(this.cmd, this.args);
                break;
            case "monitor":
                command = new Monitor(this.cmd, this.args);
                break;
            case "remove":
                command = new Remove(this.cmd, this.args);
                break;
            case "removeall":
                command = new RemoveAll(this.cmd, this.args);
                break;
            default:
                command = null;
        }
        return this;
    }

    /**
     * 执行命令
     * @return 执行结果
     */
    public Object execute() {

        if (command == null) {
            throw new RuntimeException("Command not supported");
        }
        return command.exec(this.argusUser);
    }
}