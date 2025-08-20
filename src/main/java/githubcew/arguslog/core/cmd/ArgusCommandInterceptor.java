package githubcew.arguslog.core.cmd;

import githubcew.arguslog.core.ArgusRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * @author chenenwei
 */
@ConditionalOnMissingBean(ArgusCommandInterceptor.class)
@Component
public class ArgusCommandInterceptor implements CommandInterceptor{

    @Override
    public boolean intercept(ArgusRequest request) {
        return false;
    }
}
