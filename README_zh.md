# ArgusLog 介绍

ArgusLog是一个基于SpringBoot + Websocket 开发的接口监测web端命令行工具, 主要用于开发或线上接口定位、性能优化分析，支持针对一个或多个接口的的入参、返回值、耗时、异常、调用链进行监测， 可以解决一些复杂场景下接口监测的问题。

# 特性
- 采用WebSocket连接，实时监控接口请求
- 支持多接口监控，支持模糊搜索
- 支持监控入参、返回值、耗时
- 支持接口移除监控
- 性能高、不影响原业务
- 支持多种终端，如：Web终端、websocket工具

# 使用步骤：

1. 克隆项目 
```shell
git clone https://github.com/GitHubCew/ArgusLog.git
```

2. 使用maven clean install 命令安装到本地maven仓库
```shell
maven clean install
```
或者从Maven中央仓库拉取最新依赖：

[Maven中央仓库地址(Sonatype Central)](https://central.sonatype.com/artifact/io.github.githubcew/arguslog)


3. 在项目中引用依赖:

```xml
      <dependency>
            <groupId>io.github.githubcew</groupId>
            <artifactId>arguslog</artifactId>
            <version>${version}</version> <!-- 换为实际版本号 -->
        </dependency>
```

4. 如果项目中有安全校验，则需要放开路径：
    - `/arguslog-ws`
    - `/arguslog/index.html`
      
      
   例如：Shiro中添加：
      
   ```java
   filters.put("/arguslog-ws", "anon");
   filters.put("/arguslog/index.html", "anon");
   ```
    
   SpringSecurity中添加：
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                // 放开指定的接口
                .antMatchers("/arguslog-ws", "/arguslog/index.html").permitAll()
                // 其他接口需要认证
                .anyRequest().authenticated()
            .and()
            .formLogin().disable()
            .httpBasic().disable()
            .csrf().disable(); // 根据需求决定是否禁用CSRF
    }
}
```


5. 启动项目


6. 访问项目web + `/arguslog/index.html`  
   例如： `localhost:80/context/arguslog/index.html` (context: 为项目的context-path上下文)


7. 进入arguslog,如果出现如下界面，则成功

``` shell
  ,---.                             ,--.                 
 /  O  \ ,--.--. ,---.,--.,--. ,---.|  |    ,---. ,---.  
|  .-.  ||  .--'| .-. |  ||  |(  .-'|  |   | .-. | .-. | 
|  | |  ||  |   ' '-' '  ''  '.-'  `)  '--.' '-' ' '-' ' 
`--' `--'`--'   .`-  / `----' `----'`-----' `---'.`-  /  
                `---'                            `---'  
输入 'help' 查看命令用法

$
```

8.命令介绍
- connect 连接 WebSocket 服务器
- exit/quit 断开 WebSocket 连接
- clear 清空终端屏幕
- help 显示本帮助信息
- ls [path] 查看API接口,支持模糊搜索
- lsm  查看已监测API接口
- monitor [path] [param | result | time]监控指定API接口,监控内容可选：param:参数 result:结果 time:耗时
- remove [path] 移除对指定API接口的监控
- removeall 清空全部监控的API接口

提示：使用 ↑/↓ 箭头键浏览历史命令

# 例子

假设我们有以下接口：

```java
@GetMapping("/activityWalkRouteActivity/info")
public Result<ActivityWalkRouteActivityInfoVO> info(@RequestParam("id") Long id) {
    // 接口实现
}
```

我们希望监控 `/activityWalkRouteActivity/info` 的入参和耗时，并监控返回的结果和耗时。
         

首先我们访问localhost:80/context/arguslog/index.html 进入arguslog终端，输入命令：

```shell 

# 输入connect命令连接 WebSocket 服务器
$ connect
argus connected

# 使用monitor命令监测 /activityWalkRouteActivity/info 的入参和耗时
arguslog> monitor /activityWalkRouteActivity/info param,time
ok

# 输如monitor命令监测后，需要等待或者手动调用接口/activityWalkRouteActivity/info
# 例如：curl http://localhost:8080/activityWalkRouteActivity/info?id=495


# 接口监测结果输出
[PARAM] "id":495   # 方法参数
[TIME] 151 # 方法耗时


```

