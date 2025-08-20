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
    - `/argus-ws`
    - `/argus/index.html`


例如：Shiro中添加：

   ```java
   filters.put("/argus-ws", "anon");
   filters.put("/argus/index.html", "anon");
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
                .antMatchers("/argus-ws", "/argus/index.html").permitAll()
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


6. 访问项目web + `/argus/index.html`  
   例如： `localhost:80/context/argus/index.html` (context: 为项目的context-path上下文)


7. 进入arguslog,如果出现如下界面，则成功

``` shell
  ,---.                             ,--.                 
 /  O  \ ,--.--. ,---.,--.,--. ,---.|  |    ,---. ,---.  
|  .-.  ||  .--'| .-. |  ||  |(  .-'|  |   | .-. | .-. | 
|  | |  ||  |   ' '-' '  ''  '.-'  `)  '--.' '-' ' '-' ' 
`--' `--'`--'   .`-  / `----' `----'`-----' `---'.`-  /  
                `---'                            `---'   
                                                         
          Developed by: chenenwei heyugui               
16:04:24
提示: 可以使用 ↑/↓ 方向键浏览历史命令
'connect' 连接 'exit' 或 'Ctrl+D' 关闭连接 'clear' 清屏 'auth' 认证 'help' 查看命令列表

$
```

8.命令介绍
- connect 连接 WebSocket 服务器
- exit/quit 断开 WebSocket 连接
- clear 清空终端屏幕
- auth [username] [password] [time] 认证
- help [command] 查看命令用法
- ls [-m] [path] 查看API（监听）接口,支持模糊搜索
- monitor [path] [param | result | time]监控指定API接口,监控内容可选：param:参数 result:结果 time:耗时
- remove [-a | path] 移除指定监听接口或者全部监听接口


# 例子

假设我们有以下接口：

```java
@GetMapping("/activityWalkRouteActivity/info")
public Result<ActivityWalkRouteActivityInfoVO> info(@RequestParam("id") Long id) {
    // 接口实现
}
```

如果我们要监控 `/activityWalkRouteActivity/info` 的入参和耗时，并监控返回的结果和耗时。


1 连接和监听接口

首先我们访问localhost:80/context/argus/index.html 进入arguslog终端，输入命令：

```shell
# 输入connect命令连接 WebSocket 服务器
$connect
16:43:18
Connecting to ws://localhost:8080/argus-ws...
16:43:18
argus 已连接
```

2 结果输出
```shell 


# 输如monitor命令监测后，需要等待或者手动调用接口/activityWalkRouteActivity/info
# 例如：curl http://localhost:8080/activityWalkRouteActivity/info?id=495


# 接口监测结果输出
param ==> "id":495   # 方法参数
time  ==> 151 # 方法耗时


```

