package githubcew.arguslog.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;

/**
 * Argus配置
 * @author chenenwei
 */
@ConfigurationProperties(prefix="argus")
@Data
public class ArgusProperties {

    // 默认用户名
    private String username = "argus";

    // 默认用户密码
    private String password = "argus";

    // token 刷新时间（秒）
    private Long tokenFlushTime = 60L;

    // 打印用户信息
    private Boolean printUserInfo = true;

    // 打印argus banner
    private Boolean printBanner = true;

    // token过期时间 （30分钟）
    private Long tokenExpireTime = 1000 * 60 * 30L;

    // 调用链排除包
    private String callChainExcludePackage ="sun.,java.,javax.";

}
