package githubcew.arguslog.core.cmd.system;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.ArgusManager;
import githubcew.arguslog.core.cmd.BaseCommand;
import picocli.CommandLine;

import java.util.Map;
import java.util.Objects;

/**
 * 帮助命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "help",
        description = "显示帮助信息",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class HelpCmd extends BaseCommand {

    @CommandLine.Parameters(
            index = "0",
            description = "命令名称",
            arity = "0..1"
    )
    private String command;

    /**
     * 执行逻辑
     * @return 状态码
     * @throws Exception 异常
     */
    @Override
    public Integer execute() throws Exception {

        ArgusManager argusManager = ContextUtil.getBean(ArgusManager.class);
        Map<String, Class<? extends BaseCommand>> commands = argusManager.getCommandManager().getCommands();

        // 输出可用命令
        if (Objects.isNull(this.command) || this.command.isEmpty()) {
            picocliOutput.out("Argus 可用命令：\n");
            int targetWidth = 16;

            commands.forEach((name, cmdClass) -> {
                try {
                    BaseCommand cmd = cmdClass.newInstance();
                    CommandLine commandLine = new CommandLine(cmd);
                    String description = String.join(" ", commandLine.getCommandSpec().usageMessage().description());

                    int nameDisplayWidth = displayWidth(name);
                    int padding = Math.max(1, targetWidth - nameDisplayWidth);
                    String paddedName = name + repeat(" ", padding);

                    picocliOutput.out("   " + paddedName + description);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            });
            picocliOutput.out("\n快捷键：ctrl+s 清除所有监听， ctrl+e 关闭连接，ctrl+q 登出。");
            picocliOutput.out("可使用 'help <命令>' 查看详细帮助");
        }
        // 输出指定命令使用帮助
        else {
            Class<? extends BaseCommand> specificCommand = commands.get(command);
            if (Objects.nonNull(specificCommand)) {
                BaseCommand specificCommandInstance = specificCommand.newInstance();
                new CommandLine(specificCommandInstance).usage(picocliOutput.getOut());
                picocliOutput.hasNormalOutput = true;
            }
            else {
                throw new RuntimeException(ERROR_COMMAND_NOT_FOUND);
            }
        }

        return OK_CODE;
    }

    /**
     * 重复字符串
     * @param str 字符串
     * @param count 次数
     * @return 重复的字符串
     */
    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    /**
     * 计算宽度
     * @param str 字符串
     * @return 宽度
     */
    public static int displayWidth(String str) {
        if (str == null) return 0;
        int width = 0;
        for (char c : str.toCharArray()) {
            if (c >= 0x4E00 && c <= 0x9FFF) {
                width += 2;
            } else if (c > 0x7F) {
                width += 2;
            } else {
                width += 1;
            }
        }
        return width;
    }
}
