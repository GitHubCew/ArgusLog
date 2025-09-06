package githubcew.arguslog.monitor;

import lombok.Data;

import java.lang.reflect.Method;

/**
 * 方法
 *
 * @author chenenwei
 */
@Data
public class ArgusMethod {

    /**
     * 方法名称
     */
    private String name;

    /**
     * 方法签名
     */
    private String signature;

    /**
     * 方法实例
     */
    private Method method;

    /**
     * 接口uri
     */
    private String uri;
    /**
     * 构造方法
     */
    public ArgusMethod() {
    }

    /**
     * 构造方法
     *
     * @param method 方法
     */
    public ArgusMethod(Method method) {
        this.method = method;
    }

    /**
     * 构造方法
     *
     * @param name      参数
     * @param signature 方法签名
     * @param method    方法实例
     * @param uri       接口uri
     */
    public ArgusMethod(String name, String signature, Method method, String uri) {
        this.name = name;
        this.signature = signature;
        this.method = method;
        this.uri = uri;
    }
}
