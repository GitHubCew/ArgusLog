package githubcew.arguslog.business.cmd;

import githubcew.arguslog.core.Cache;
import githubcew.arguslog.core.Constant;
import githubcew.arguslog.core.MonitorInfo;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

/**
 * 监控命令
 * @author  chenenwei
 */
public class Monitor extends CommonCmd{

    /**
     * 构造函数
     * @param args 参数
     */
    public Monitor(String args) {
        super(args);
    }

    /**
     * 检查参数是否正确
     * @param args 参数
     * @return 错误信息
     */
    @Override
    public String check(String[] args) {

        if (args.length != 1 && args.length != 2) {
            return error("参数错误,格式为：monitor [接口路径] [param|result|time|ex,stack]");
        }
        if (!Cache.hasUri(args[0])) {
            return error("参数错误,接口路径不存在");
        }

        if (args.length == 2) {
            List<String> apiContents = Arrays.asList("param", "result", "time", "ex","stack");
            boolean include = Arrays.stream(args[1].split(",")).anyMatch(s -> apiContents.contains(s.trim()));
            if (!include) {
                return error("参数错误,可选值 [param|result|time|ex|stack]") ;
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
    public String execute(String user, String cmd, String[] args) {
        Method method = Cache.getMethod(args[0].trim());
        if (args.length == 1) {
            Cache.addMethod(user, new MonitorInfo(method, true, true, true, true,true));
        }
        else if (args.length == 2) {
            String trimCmd = args[1].trim();
            boolean param = false;
            boolean result = false;
            boolean time = false;
            boolean ex = false;
            boolean stackTrace =  false;
            if (trimCmd.contains("param")) {
                param = true;
            }
            if (trimCmd.contains("result")) {
                result = true;
            }
            if (trimCmd.contains("time")) {
                time = true;
            }
            if (trimCmd.contains("ex")) {
                ex = true;
            }
            if (trimCmd.contains("stack")) {
                stackTrace = true;
            }
            Cache.addMethod(user, new MonitorInfo(method, param, result, time, ex,stackTrace));
        }
        return Constant.OK;
    }
}
