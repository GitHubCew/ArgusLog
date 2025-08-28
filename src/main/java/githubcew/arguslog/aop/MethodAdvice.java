package githubcew.arguslog.aop;

import githubcew.arguslog.common.util.ContextUtil;
import githubcew.arguslog.core.cache.ArgusCache;
import githubcew.arguslog.monitor.MonitorOutput;
import githubcew.arguslog.monitor.WebRequestInfo;
import githubcew.arguslog.monitor.formater.MethodParamFormatter;
import githubcew.arguslog.monitor.outer.Outer;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 方法增强
 *
 * @author chenenwei
 */
public class MethodAdvice implements MethodInterceptor {

    /**
     * 拦截方法
     *
     * @param invocation 方法调用
     * @return 返回值
     * @throws Throwable 异常
     */
    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {

        boolean hasMethod = ArgusCache.containsMethod(invocation.getMethod());
        Object returnVal = null;
        if (!hasMethod) {
            returnVal = invocation.proceed();
            return returnVal;
        }

        MonitorOutput monitorOutput = new MonitorOutput();

        try {
            WebRequestInfo webRequestInfo = RequestParamExtractor.extractRequestInfo();
            monitorOutput.setWebRequestInfo(webRequestInfo);
        }
        catch (Exception e) {
            //
        }

        long start = 0;
        long end = 0;

        try {
            // 参数格式化
            MethodParamFormatter formatter = ContextUtil.getBean(MethodParamFormatter.class);
            Object format = formatter.format(invocation.getMethod().getParameters(), invocation.getArguments());
            monitorOutput.setMethodParam(format);
            // 调用链信息
            monitorOutput.setCallChain(new RuntimeException().getStackTrace());

            start = System.currentTimeMillis();

            // 执行原方法
            returnVal = invocation.proceed();
            monitorOutput.setResult(returnVal);

            end = System.currentTimeMillis();
        } catch (Exception e) {
            end = System.currentTimeMillis();
            monitorOutput.setException(e);
            throw e;
        } finally {
            // 计算耗时
            monitorOutput.setTime(end - start);
            // 输出content
            Outer outer = ContextUtil.getBean(Outer.class);
            outer.out(invocation.getMethod(), monitorOutput);
        }
        return returnVal;
    }

    /**
     * Web请求参数提取工具类
     */
    public static class RequestParamExtractor {

        /**
         * 提取请求参数（支持所有HTTP方法）
         * @param request 请求
         * @return 请求参数
         */
        public static String extractRawRequestParams(HttpServletRequest request) {
            if (request == null) {
                return "";
            }

            String method = request.getMethod().toUpperCase();
            String contentType = request.getContentType();

            try {
                // GET、DELETE 等请求通常使用Query参数
                if ("GET".equals(method) || "DELETE".equals(method) ||
                        "HEAD".equals(method) || "OPTIONS".equals(method)) {
                    return extractQueryString(request);
                }

                // POST、PUT、PATCH 等请求可能包含body
                if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
                    // 检查是否有Content-Type
                    if (contentType != null) {
                        if (contentType.contains("application/json")) {
                            return extractJsonBodySafely(request);
                        } else if (contentType.contains("application/x-www-form-urlencoded")) {
                            // 表单提交，优先使用getParameter获取
                            String formParams = extractQueryString(request);
                            if (!formParams.isEmpty()) {
                                return formParams;
                            }
                            // 如果getParameter为空，尝试读取body
                            return extractFormBodySafely(request);
                        } else if (contentType.contains("multipart/form-data")) {
                            // 文件上传表单，只提取非文件参数
                            return extractQueryString(request);
                        } else {
                            // 其他Content-Type，尝试读取body
                            return extractRequestBodySafely(request);
                        }
                    } else {
                        // 没有Content-Type，尝试读取body
                        return extractRequestBodySafely(request);
                    }
                }

                // 默认返回Query参数
                return extractQueryString(request);

            } catch (Exception e) {
                return "提取参数失败: " + e.getMessage();
            }
        }

        /**
         * 安全地提取JSON body内容
         * @param request 请求
         * @return JSON body内容
         */
        private static String extractJsonBodySafely(HttpServletRequest request) {
            try {
                String body = extractRequestBodySafely(request);
                return body.isEmpty() ? extractQueryString(request) : body;
            } catch (Exception e) {
                return "无法读取JSON body: " + e.getMessage();
            }
        }

        /**
         * 安全地提取表单body内容
         * @param request 请求
         * @return 表单body内容
         */
        private static String extractFormBodySafely(HttpServletRequest request) {
            try {
                String body = extractRequestBodySafely(request);
                return body.isEmpty() ? "" : body;
            } catch (Exception e) {
                return "无法读取表单body: " + e.getMessage();
            }
        }

        /**
         * 提取请求体内容
         * @param request 请求
         * @return 请求体内容
         */
        private static String extractRequestBodySafely(HttpServletRequest request) {
            StringBuilder builder = new StringBuilder();
            // 先尝试从getParameter获取（对于表单数据）
            String queryParams = extractQueryString(request);
            if (!queryParams.isEmpty()) {
                builder.append(queryParams);
            }

            // 检查是否有请求体内容
            if (request.getContentLength() <= 0) {
                return builder.toString();
            }

            try {
                // 如果请求已经被包装过（有缓存）
                if (request instanceof ContentCachingRequestWrapper) {
                    ContentCachingRequestWrapper wrappedRequest = (ContentCachingRequestWrapper) request;
                    if (!builder.toString().isEmpty()) {
                        builder.append("; ");
                    }
                    return builder + "\n" +  new String(wrappedRequest.getContentAsByteArray(), StandardCharsets.UTF_8);
                }

                // 尝试读取输入流
                BufferedReader reader = request.getReader();
                String line;
                builder.append("; ");
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }

                return builder.toString().trim();

            } catch (IllegalStateException e) {
                // 输入流已经被读取过
                return "请求体已被读取";
            } catch (Exception e) {
                return "读取请求体失败: " + e.getMessage();
            }
        }

        /**
         * 提取query参数
         * @param request 请求
         * @return query参数
         */
        private static String extractQueryString(HttpServletRequest request) {
            StringBuilder queryString = new StringBuilder();
            Enumeration<String> parameterNames = request.getParameterNames();

            while (parameterNames.hasMoreElements()) {
                String paramName = parameterNames.nextElement();
                String[] values = request.getParameterValues(paramName);

                if (values != null && values.length > 0) {
                    for (String value : values) {
                        if (queryString.length() > 0) {
                            queryString.append("&");
                        }
                        // URL编码处理
                        queryString.append(paramName).append("=").append(value != null ? value : "");
                    }
                }
            }

            return queryString.toString();
        }

        /**
         * 提取请求头
         * @param request 请求
         * @return 请求头
         */
        public static String extractFilteredHeaders(HttpServletRequest request) {
            if (request == null) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            Enumeration<String> headerNames = request.getHeaderNames();

            while (headerNames.hasMoreElements()) {
                String headerName = headerNames.nextElement();
                String headerValue = request.getHeader(headerName);
                builder.append(headerName).append("=").append(headerValue);

            }

            return builder.toString();
        }

        /**
         * 获取当前请求的HttpServletRequest
         * @return HttpServletRequest
         */
        public static HttpServletRequest getHttpServletRequest() {
            try {
                ServletRequestAttributes attributes = (ServletRequestAttributes)
                        RequestContextHolder.getRequestAttributes();
                return attributes != null ? attributes.getRequest() : null;
            } catch (Exception e) {
                return null;
            }
        }

        /**
         * 完整的请求信息提取
         * @return WebRequestInfo
         */
        public static WebRequestInfo extractRequestInfo() {
            HttpServletRequest request = getHttpServletRequest();
            if (request == null) {
                return null;
            }

            WebRequestInfo requestInfo = new WebRequestInfo();

            try {
                // 基本信息
                requestInfo.setUrl(request.getRequestURL().toString());
                requestInfo.setMethod(request.getMethod());
                requestInfo.setContentType(request.getContentType());
                requestInfo.setIp(getClientIp(request));
                // 请求参数
                requestInfo.setRawParams(extractRawRequestParams(request));

                // 头部信息
                requestInfo.setHeaders(extractFilteredHeaders(request));

            } catch (Exception e) {
                e.printStackTrace();
            }

            return requestInfo;
        }

        /**
         * 获取客户端真实IP
         * @param request 请求
         * @return IP
         */
        private static String getClientIp(HttpServletRequest request) {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            return ip;
        }
    }
}
