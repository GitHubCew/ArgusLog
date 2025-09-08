package githubcew.arguslog.core.anno;

import java.lang.annotation.*;

/**
 * 属性信息
 *
 * @author chenenwei
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ArgusProperty {

    /**
     * 配置描述
     * @return 描述
     */
    String description() default "";

    /**
     * 是否可在运行时修改
     * @return 结果
     */
    boolean modifyInRunning() default false;

    /**
     * 实现显示子show命令中
     * @return 结果
     */
    boolean displayInShow() default true;
}
