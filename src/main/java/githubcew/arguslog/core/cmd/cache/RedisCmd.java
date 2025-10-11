package githubcew.arguslog.core.cmd.cache;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.cmd.BaseCommand;
import githubcew.arguslog.monitor.outer.OutputWrapper;
import picocli.CommandLine;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * redis命令
 *
 * @author chenenwei
 */
@CommandLine.Command(
        name = "redis",
        description = "redis命令",
        mixinStandardHelpOptions = true,
        version = "1.0"
)
public class RedisCmd extends BaseCommand {

    @CommandLine.Parameters(
            index = "0",
            description = "操作类型(支持 list,get,set,delete,expire)",
            arity = "1",
            paramLabel="operatorType"
    )
    private String operatorType;

    @CommandLine.Parameters(
            index = "1",
            description = "key",
            arity = "0..1",
            paramLabel="key"
    )
    private String key;

    @CommandLine.Parameters(
            index = "2",
            description = "value",
            arity = "0..1",
            paramLabel="value"
    )
    private String value;


    @Override
    protected Integer execute() throws Exception {
        try {
            // 1. 加载 StringRedisTemplate 类
            Class<?> stringRedisTemplateClass = Class.forName("org.springframework.data.redis.core.StringRedisTemplate");

            // 2. 从容器获取 StringRedisTemplate
            Object stringRedisTemplate = null;
            try {
                stringRedisTemplate = ContextUtil.getBean(stringRedisTemplateClass);
            } catch (Exception e) {
                throw new RuntimeException("无法执行 Redis 命令：项目未配置 Redis（StringRedisTemplate 不存在）");
            }

            // 3. 根据操作类型执行
            switch (operatorType) {
                case "list": {
                    if (Objects.isNull(key)) {
                        key = "*";
                    } else {
                        key = "*" + key + "*";
                    }
                    Method keysMethod = stringRedisTemplateClass.getMethod("keys", Object.class);
                    @SuppressWarnings("unchecked")
                    Set<String> keySet = (Set<String>) keysMethod.invoke(stringRedisTemplate, key);

                    List<String> keys = new ArrayList<>(keySet);
                    long total = keys.size();
                    if (keys.size() > 50) {
                        keys = new ArrayList<>(keys.subList(0, 50)); // 避免视图问题
                    }
                    String output = OutputWrapper.wrapperCopy(keys, "\n") + "\n(" + total + ")";
                    picocliOutput.out(output);
                    break;
                }

                // 获取key
                case "get": {
                    if (Objects.isNull(key)) {
                        throw new RuntimeException("请输入 key");
                    }
                    Class<?> valueOperationsClass = Class.forName("org.springframework.data.redis.core.ValueOperations");
                    Object valueOps = stringRedisTemplateClass.getMethod("opsForValue").invoke(stringRedisTemplate);
                    Method getMethod = valueOperationsClass.getMethod("get", Object.class);
                    Object result = getMethod.invoke(valueOps, key);
                    picocliOutput.out(result != null ? OutputWrapper.wrapperCopy(result.toString()) : "");
                    break;
                }

                // 设置key
                case "set": {
                    if (Objects.isNull(key)) {
                        throw new RuntimeException("请输入 key");
                    }
                    if (Objects.isNull(value)) {
                        throw new RuntimeException("请输入 value");
                    }
                    Class<?> valueOperationsClass = Class.forName("org.springframework.data.redis.core.ValueOperations");
                    Object valueOps = stringRedisTemplateClass.getMethod("opsForValue").invoke(stringRedisTemplate);
                    Method setMethod = valueOperationsClass.getMethod("set", Object.class, Object.class);
                    setMethod.invoke(valueOps, key, value);
                    picocliOutput.out("OK");
                    break;
                }

                // 设置过期时间（单位：秒）
                case "expire": {
                    if (Objects.isNull(key)) {
                        throw new RuntimeException("请输入 key");
                    }
                    if (Objects.isNull(value)) {
                        throw new RuntimeException("请输入过期时间（秒）");
                    }
                    long expireSeconds;
                    try {
                        expireSeconds = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        throw new RuntimeException("过期时间必须是数字（秒）");
                    }

                    // 调用 RedisTemplate.expire(key, timeout, TimeUnit.SECONDS)
                    Class<?> timeUnitClass = Class.forName("java.util.concurrent.TimeUnit");
                    Object secondsUnit = timeUnitClass.getField("SECONDS").get(null);

                    Method expireMethod = stringRedisTemplateClass.getMethod("expire", Object.class, long.class, timeUnitClass);
                    Boolean result = (Boolean) expireMethod.invoke(stringRedisTemplate, key, expireSeconds, secondsUnit);
                    if (!result) {
                        throw new RuntimeException("设置过期时间失败");
                    }
                    break;
                }

                // 删除 key
                case "delete": {
                    if (Objects.isNull(key)) {
                        throw new RuntimeException("请输入 key");
                    }
                    // 调用 RedisTemplate.delete(key)
                    Method deleteMethod = stringRedisTemplateClass.getMethod("delete", Object.class);
                    Object invoke = deleteMethod.invoke(stringRedisTemplate, key);
                    if (invoke instanceof Boolean) {
                        Boolean deleted = (Boolean) invoke;
                        if (!deleted) {
                            throw new RuntimeException("删除失败");
                        }
                    }
                    if (invoke instanceof Long) {
                        Long deletedCount = (Long) invoke;
                        picocliOutput.out("Deleted " + deletedCount + " key(s)");
                    }
                    break;
                }

                default:
                    throw new RuntimeException("不支持的操作类型: " + operatorType);
            }

            return OK_CODE;

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("项目未引入Redis", e);
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw e;
            }
            throw new RuntimeException("执行 Redis 命令失败: " + e.getMessage(), e);
        }
    }
}
