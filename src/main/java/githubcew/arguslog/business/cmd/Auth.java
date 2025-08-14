package githubcew.arguslog.business.cmd;

import githubcew.arguslog.business.auth.ArgusUser;
import githubcew.arguslog.core.Cache;
import githubcew.arguslog.core.Constant;

import java.util.UUID;

/**
 * 认证命令
 * @author chenenwei
 */
public class Auth extends BaseCmd {

    /**
     * 构造函数
     * @param args 参数
     */
    public Auth(String cmd, String[] args) {
        super(cmd, args);
    }

    /**
     * 检查参数是否正确
     * @param args 参数
     * @return 错误信息
     */
    @Override
    public String check(String[] args) {

        if (args.length < 2 || args.length > 3) {
            return error("参数错误: 格式为：auth [username] [password] [time]");
        }
        if (args.length == 3) {
            try {
                Integer.parseInt(args[2]);
            } catch (Exception e) {
                return error("参数错误: 过期时间time只能是数字");
            }
        }
        return Constant.EMPTY;
    }

    /**
     * 执行命令
     * @param user 用户
     * @param cmd 命令
     * @param args 参数
     * @return 执行结果
     */
    @Override
    public String execute(ArgusUser user, String cmd, String[] args) {

        String token = UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        // 默认过期时间 1h
        long defaultTime = 60 * 60 * 1000;
        if (args.length == 3) {
            defaultTime = Long.parseLong(args[2]) * 1000;
        }
        user.setExpireTime(System.currentTimeMillis() + defaultTime);
        Cache.addCredentials(token, user);
        return token + Constant.TOKEN_SPLIT + Constant.OK;
    }
}
