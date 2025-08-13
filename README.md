# ArgusLog Documentation
ArgusLog is a web-based command-line monitoring tool developed with SpringBoot and WebSocket, primarily designed for troubleshooting and performance optimization analysis during development or production. It supports monitoring of request parameters, return values, execution time, exceptions, and call chains for one or multiple APIs, helping to resolve interface monitoring challenges in complex scenarios.

# Key Features
- Real-time monitoring via WebSocket connections
- Support for monitoring multiple interfaces with fuzzy search capability
- Tracks input parameters, return values, and execution time
- Allows removal of monitored interfaces
- High performance with minimal impact on original business operations
- Compatible with various clients including web terminals and WebSocket tools

# Usage
1.Clone the repository:
```shell
git clone https://github.com/GitHubCew/ArgusLog.git
```

2.Install to local Maven repository:
```shell
mvn clean install
```

Alternatively, pull the latest dependency from Maven Central:
[Maven Central Repository (Sonatype)](https://central.sonatype.com/artifact/io.github.githubcew/arguslog)

3.Add dependency to your project:
```xml
<dependency>
    <groupId>io.github.githubcew</groupId>
    <artifactId>arguslog</artifactId>
    <version>${version}</version> <!-- Replace with actual version -->
</dependency>
```

4.Security Configuration
  
If your project has security validation, whitelist these paths:

- /arguslog-ws
- /arguslog/index.html

Example for Shiro:
```java
filters.put("/arguslog-ws", "anon");
filters.put("/arguslog/index.html", "anon");
```
Example for Spring Security:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
                .authorizeRequests()
                .antMatchers("/arguslog-ws", "/arguslog/index.html").permitAll()
                .anyRequest().authenticated()
                .and()
                .formLogin().disable()
                .httpBasic().disable()
                .csrf().disable();

```

5.Getting Started
   
- Start your application
- Access the web interface at:

```shell
[your-domain]/[context-path]/arguslog/index.html
```

**Example**: localhost:8080/myapp/arguslog/index.html

**Upon successful connection, you'll see**:

```shell
  ,---.                             ,--.                 
 /  O  \ ,--.--. ,---.,--.,--. ,---.|  |    ,---. ,---.  
|  .-.  ||  .--'| .-. |  ||  |(  .-'|  |   | .-. | .-. | 
|  | |  ||  |   ' '-' '  ''  '.-'  `)  '--.' '-' ' '-' ' 
`--' `--'`--'   .`-  / `----' `----'`-----' `---'.`-  /  
                `---'                            `---'  
Input 'help' to view command usage.
 
 
 $
```

# Command Reference
**connect** - Establish WebSocket connection

**exit/qui**t： Terminate WebSocket connection

**clear**： Clear terminal screen

**help**： Display help information

**ls** [path]： List API interfaces (supports fuzzy search)

**lsm** ： List Monitored API interfaces

**monitor** [path] [param | result | time]： Monitor specific API (options: param, result, time)

**remove** [path]：Remove monitoring for specified API

**clearall**： Clear all monitored APIs

**Tip**: Use ↑/↓ arrow keys to navigate command history

# Example Usage

Consider this sample API:
```java
@GetMapping("/activityWalkRouteActivity/info")
public Result<ActivityWalkRouteActivityInfoVO> info(@RequestParam("id") Long id) {
    // Implementation
}

```

To monitor input parameters and execution time for /activityWalkRouteActivity/info:

1.Connect to the terminal and establish WebSocket connection:
```shell
$ connect
arguslog connected
```

2.Start monitoring:
```shell
arguslog> monitor /activityWalkRouteActivity/info param,time
ok
```
3.Trigger the API (e.g., via curl):
```shell
curl http://localhost:8080/activityWalkRouteActivity/info?id=495
```

4.View monitoring output:
```text
arguslog>monitor /activityWalkRouteActivity/info param,time
ok

[PARAM] "id":495   # Method parameter
[TIME] 151        # Execution time (ms)
```