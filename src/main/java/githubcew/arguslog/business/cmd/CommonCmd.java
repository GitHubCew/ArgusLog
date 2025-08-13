package githubcew.arguslog.business.cmd;

import githubcew.arguslog.core.Constant;

import java.util.Arrays;
import java.util.Objects;

/**
 * 通用命令实现类
 * @author  chenenwei
 */
public class CommonCmd implements Cmd{

    /**
     * 命令
     */
    protected String cmd;

    /**
     * 参数
     */
    protected String[] args;

    /**
     * 获取命令
     * @return 命令
     */
    public String getCmd() {
        return cmd;
    }

    /**
     * 设置命令
     * @param cmd 命令
     */
    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    /**
     * 获取参数
     * @return 参数
     */
    public String[] getArgs() {
        return args;
    }

    /**
     * 设置参数
     * @param args 参数
     */
    public void setArgs(String[] args) {
        this.args = args;
    }

    /**
     * 构造函数
     * @param args 参数
     */
    public CommonCmd (String args) {

        String[] parts = args.trim().split(Constant.SPACE_PATTERN);

        // 命令
        String command = parts.length > 0 ? parts[0] : "";

        // 参数
        String[] arguments = parts.length > 1
                ? Arrays.copyOfRange(parts, 1, parts.length)
                : new String[0];
        this.cmd = command;
        this.args = arguments;
    }

    /**
     * 检查参数是否合法
     * @param args 参数
     * @return 错误信息
     */
    @Override
    public String check(String[] args){
        return null;
    }

    /**
     * 执行命令
     * @param user 用户
     * @param cmd 命令
     * @param args 参数
     * @return 执行结果
     */
    @Override
    public Object execute(String user, String cmd, String[] args) {
        return null;
    }

    /**
     * 执行命令
     * @param user 用户
     * @return 执行结果
     */
    public Object exec(String user) {

        try {
            String check = check(this.args);
            if (!Objects.isNull(check) && !check.isEmpty()) {
                return check;
            }
            Object result = execute(user, this.cmd, this.args);
            return result.equals(Constant.OK) ? Constant.OK : result;
        } catch (Exception e) {
            return error(e.getMessage());
        }
    }

    /**
     * 错误信息
     * @param msg 消息
     * @return
     */
    protected String error (String msg) {
        return Constant.OUT_ERROR + msg;
    }
}
