package githubcew.arguslog.web;

import githubcew.arguslog.core.ArgusConstant;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * argus Web
 * @author chenenwei
 */
@Controller
public class ArgusWeb {

    @GetMapping("/argus/index.html")
    public ResponseEntity<byte[]> argus(
            HttpServletRequest request,
            @RequestHeader(value = "Accept-Language", required = false) String acceptLanguage) throws IOException {

        // 1. 加载HTML模板
        ClassPathResource resource = new ClassPathResource(ArgusConstant.ARGUS_TERMINAL_HTML);
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }

        // 2. 确定用户首选语言
        String language = determineLanguage(acceptLanguage);

        // 3. 加载对应语言资源
        Map<String, String> i18nMap = loadI18nResources(language);

        // 4. 读取HTML内容并替换国际化标签
        String htmlContent = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        htmlContent = processPlaceholder(htmlContent, i18nMap);

        // 5. 返回处理后的HTML
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(htmlContent.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 推断语言
     * @param acceptLanguage 语言
     * @return 语言
     */
    private String determineLanguage(String acceptLanguage) {
        if (acceptLanguage == null || acceptLanguage.isEmpty()) {
            return "en"; // 默认英文
        }

        // 解析Accept-Language头部
        String[] languages = acceptLanguage.split(",");
        for (String lang : languages) {
            String[] parts = lang.split(";");
            String language = parts[0].trim().toLowerCase();

            if (language.startsWith("zh")) {
                return "zh";
            }
            if (language.startsWith("en")) {
                return "en";
            }
        }

        return "en"; // 默认英文
    }

    /**
     * 加载多语言文件
     * @param language 语言
     * @return map
     * @throws IOException 异常
     */
    private Map<String, String> loadI18nResources(String language) throws IOException {
        String i18nFilePath = String.format("locales/%s.json", language);
        ClassPathResource i18nResource = new ClassPathResource(ArgusConstant.BASE_RESOURCE_PATH + i18nFilePath);

        if (!i18nResource.exists()) {
            // 如果指定语言文件不存在，默认英文
            i18nResource = new ClassPathResource(ArgusConstant.BASE_RESOURCE_PATH + "locales/en.json");
        }

        // 这里简化处理，实际可以使用Jackson等JSON库
        String jsonContent = StreamUtils.copyToString(i18nResource.getInputStream(), StandardCharsets.UTF_8);
        return json2Map(jsonContent);
    }

    /**
     * 转json 为 map
     * @param json  json
     * @return map
     */
    private Map<String, String> json2Map(String json) {
        // 简化实现，实际项目应使用JSON解析库
        Map<String, String> map = new HashMap<>();
        json = json.replaceAll("[{}\"]", "").trim();

        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                map.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }

        return map;
    }

    /**
     * 处理占位符
     * @param html html
     * @param i18nMap map
     * @return
     */
    private String processPlaceholder(String html, Map<String, String> i18nMap) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = ArgusConstant.I18N_PATTERN.matcher(html);

        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = i18nMap.getOrDefault(key, key);
            matcher.appendReplacement(result, replacement);
        }
        matcher.appendTail(result);

        return result.toString();
    }
}
