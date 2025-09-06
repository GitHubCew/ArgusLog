package githubcew.arguslog.web.filter;

import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.util.ContentCachingRequestWrapper;
import java.io.IOException;

/**
 * 用于缓存 HTTP 请求体的过滤器。
 * 该过滤器会将 HttpServletRequest 包装成 ContentCachingRequestWrapper，
 * 从而允许请求体被多次读取，解决了流只能读一次的问题。
 * 它会跳过文件上传（multipart）请求和过大的请求。
 */
public class RequestBodyCachingFilter extends OncePerRequestFilter {

    // 请求体大小限制：2MB
    private static final int MAX_BODY_SIZE = 1024 * 1024 * 2;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // 判断是否有请求体
        String method = request.getMethod().toUpperCase();
        boolean hasBody = "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);

        // 跳过 multipart（文件上传）
        String contentType = request.getContentType();
        boolean isMultipart = contentType != null && contentType.contains("multipart/");

        // 跳过无 body、文件上传或超大请求
        if (!hasBody || isMultipart || contentLengthExceedsLimit(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // 包装请求
        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        filterChain.doFilter(wrappedRequest, response);
    }

    /**
     * 检查请求体大小是否超过限制
     * @param request HttpServletRequest
     * @return 如果超过限制或长度未知则返回 true
     */
    private boolean contentLengthExceedsLimit(HttpServletRequest request) {
        int contentLength = request.getContentLength();
        return contentLength > MAX_BODY_SIZE || contentLength == -1;
    }
}