package githubcew.arguslog.monitor;

import githubcew.arguslog.core.account.Account;
import lombok.Data;

/**
 * 请求信息
 *
 * @author chenenwei
 */
@Data
public class WebRequestInfo {

    /**
     * url
     */
    private String url;
    /**
     * 方法
     */
    private String method;
    /**
     * contentType
     */
    private String contentType;
    /**
     * 请求地址
     */
    private String ip;
    /**
     * 原始参数
     */
    private String rawParams;
    /**
     * 请求头
     */
    private String headers;
    /**
     * 请求用户名
     */
    private String username;
}
