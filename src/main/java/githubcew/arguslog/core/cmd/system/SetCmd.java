package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.common.util.StringUtil;
import githubcew.arguslog.common.util.TypeUtil;
import githubcew.arguslog.config.ArgusProperties;
import githubcew.arguslog.core.cmd.BaseCommand;
import picocli.CommandLine;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 设置命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "set",
        description = "系统变量设置",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class SetCmd extends BaseCommand {

    @CommandLine.Parameters(
            index = "0",
            description = "系统变量名",
            arity = "1",
            paramLabel = "variable"
    )
    private String variable;

    @CommandLine.Parameters(
            index = "1",
            description = "变量值",
            arity = "1..*",
            paramLabel = "values"
    )
    private List<String> values;

    /**
     * 执行逻辑
     *
     * @return 结果
     * @throws Exception 异常
     */
    @Override
    protected Integer execute() throws Exception {
        checkAndSet();
        return OK_CODE;
    }

    private void checkAndSet() {

        ArgusProperties argusProperties = ContextUtil.getBean(ArgusProperties.class);

        try {
            Field variableField = argusProperties.getClass().getDeclaredField(variable);
            variableField.setAccessible(true);

            if (TypeUtil.isRawType(variableField, int.class) || TypeUtil.isRawType(variableField, Integer.class)) {
                variableField.set(argusProperties, Integer.parseInt(values.get(0)));
            } else if (TypeUtil.isRawType(variableField, long.class) || TypeUtil.isRawType(variableField, Long.class)) {
                variableField.set(argusProperties, Long.parseLong(values.get(0)));
            } else if (TypeUtil.isRawType(variableField, boolean.class) || TypeUtil.isRawType(variableField, Boolean.class)) {
                if (!StringUtil.isBooleanValue(values.get(0))) {
                    throw new RuntimeException("Value type error : " + variable);
                }
                variableField.set(argusProperties, Boolean.parseBoolean(values.get(0)));
            } else if (TypeUtil.isRawType(variableField, String.class)) {
                variableField.set(argusProperties, values.get(0));
            } else if (TypeUtil.isSetOfString(variableField)) {
                variableField.set(argusProperties, new HashSet<>(values));
            } else if (TypeUtil.isListOfString(variableField)) {
                variableField.set(argusProperties, new ArrayList<>(values));
            }

        } catch (NoSuchFieldException e) {
            throw new RuntimeException("Variable not found: " + variable);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
}
