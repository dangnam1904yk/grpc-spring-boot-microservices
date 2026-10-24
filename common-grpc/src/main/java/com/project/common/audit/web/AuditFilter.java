package com.project.common.audit.web;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.WebUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.common.audit.AuditContext;
import com.project.common.audit.AuditEvent;
import com.project.common.audit.AuditProperties;
import com.project.common.audit.AuditSink;
import com.project.common.audit.AuditType;
import com.project.common.audit.Sanitizer;
import com.project.common.id.UuidV7;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/**
 * Ghi log mỗi request REST: IP client, lệnh curl, header, request body, response body, status, thời gian xử lý.
 * <p>
 * Chạy trước Spring Security để ghi cả request bị từ chối (401/403). User lấy từ {@link AuditUserFilter}.
 * Controller trả {@code CompletableFuture} (async): response chỉ có ở lần async dispatch, nên log được ghi
 * ở lần đó (cách làm giống ShallowEtagHeaderFilter của Spring).
 */
@Slf4j
public class AuditFilter extends OncePerRequestFilter {

    private static final String START_ATTRIBUTE = AuditFilter.class.getName() + ".start";
    private static final String REQUEST_ID_ATTRIBUTE = AuditFilter.class.getName() + ".requestId";

    /** Header không đưa vào lệnh curl: do proxy / client tự sinh, chạy lại sẽ sai. */
    private static final Set<String> CURL_SKIPPED_HEADERS = Set.of("host", "content-length", "connection",
            "forwarded", "accept-encoding", "transfer-encoding");

    private final AuditSink sink;
    private final AuditProperties properties;
    private final Sanitizer sanitizer;
    private final ObjectMapper objectMapper;
    private final String serviceName;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public AuditFilter(AuditSink sink, AuditProperties properties, ObjectMapper objectMapper, String serviceName) {
        this.sink = sink;
        this.properties = properties;
        this.sanitizer = new Sanitizer(properties);
        this.objectMapper = objectMapper;
        this.serviceName = serviceName;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return matches(properties.getHttp().getExcludePaths(), request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper req;
        ContentCachingResponseWrapper res;
        if (isAsyncDispatch(request)) {
            // Async dispatch nhận lại đúng wrapper đã tạo ở lần dispatch đầu
            req = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
            res = WebUtils.getNativeResponse(response, ContentCachingResponseWrapper.class);
            if (req == null || res == null) {
                chain.doFilter(request, response);
                return;
            }
        } else {
            req = new ContentCachingRequestWrapper(request, properties.getHttp().getMaxBodyLength());
            res = new ContentCachingResponseWrapper(response);
            String requestId = AuditContext.requestIdOrNew(request.getHeader(AuditContext.REQUEST_ID_HEADER));
            req.setAttribute(START_ATTRIBUTE, System.nanoTime());
            req.setAttribute(REQUEST_ID_ATTRIBUTE, requestId);
            res.setHeader(AuditContext.REQUEST_ID_HEADER, requestId);
        }

        AuditContext.bind((String) req.getAttribute(REQUEST_ID_ATTRIBUTE), clientIp(req),
                (AuditContext.User) req.getAttribute(AuditUserFilter.USER_ATTRIBUTE));
        Exception failure = null;
        try {
            chain.doFilter(isAsyncDispatch(request) ? request : req, isAsyncDispatch(request) ? response : res);
        } catch (IOException | ServletException | RuntimeException e) {
            failure = e;
            throw e;
        } finally {
            try {
                // Request async chưa xong: đợi lần async dispatch rồi mới ghi log và trả body
                if (!req.isAsyncStarted()) {
                    record(req, res, failure);
                    res.copyBodyToResponse();
                }
            } finally {
                AuditContext.clear();
            }
        }
    }

    private void record(ContentCachingRequestWrapper req, ContentCachingResponseWrapper res, Exception failure) {
        try {
            Long start = (Long) req.getAttribute(START_ATTRIBUTE);
            long durationMs = start == null ? 0 : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            String url = fullUrl(req);

            Map<String, List<String>> headers = new LinkedHashMap<>();
            List<Map.Entry<String, String>> curlHeaders = new ArrayList<>();
            for (String name : Collections.list(req.getHeaderNames())) {
                for (String value : Collections.list(req.getHeaders(name))) {
                    String safeValue = sanitizer.header(name, value);
                    headers.computeIfAbsent(name, k -> new ArrayList<>()).add(safeValue);
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (!CURL_SKIPPED_HEADERS.contains(lower) && !lower.startsWith("x-forwarded-")) {
                        curlHeaders.add(Map.entry(name, safeValue));
                    }
                }
            }

            String requestBody = body(req.getContentAsByteArray(), req.getContentType(), req.getCharacterEncoding());
            String responseBody = matches(properties.getHttp().getExcludeResponseBodyPaths(), req)
                    ? null
                    : body(res.getContentAsByteArray(), res.getContentType(), res.getCharacterEncoding());
            AuditContext.User user = (AuditContext.User) req.getAttribute(AuditUserFilter.USER_ATTRIBUTE);

            sink.publish(AuditEvent.builder()
                    .id(UuidV7.next())
                    .type(AuditType.HTTP)
                    .requestId((String) req.getAttribute(REQUEST_ID_ATTRIBUTE))
                    .service(serviceName)
                    .timestamp(Instant.now())
                    .clientIp(clientIp(req))
                    .userId(user == null ? null : user.id())
                    .userEmail(user == null ? null : user.email())
                    .method(req.getMethod())
                    .url(url)
                    .curl(CurlBuilder.build(req.getMethod(), url, curlHeaders, requestBody))
                    .requestHeaders(objectMapper.writeValueAsString(headers))
                    .requestBody(requestBody)
                    // Exception lọt ra khỏi filter: container sẽ trả 500
                    .status(String.valueOf(failure != null && !res.isCommitted() ? 500 : res.getStatus()))
                    .responseBody(responseBody)
                    .durationMs(durationMs)
                    .error(failure == null ? null : failure.toString())
                    .build());
        } catch (Exception e) {
            log.warn("Không tạo được audit log cho {} {}: {}", req.getMethod(), req.getRequestURI(), e.toString());
        }
    }

    private String body(byte[] content, String contentType, String encoding) {
        if (content.length == 0) {
            return null;
        }
        if (!isText(contentType)) {
            return "[" + (contentType == null ? "binary" : contentType) + ", " + content.length + " bytes]";
        }
        Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
        return sanitizer.body(new String(content, charset), contentType);
    }

    private static boolean isText(String contentType) {
        if (contentType == null) {
            return true;
        }
        String type = contentType.toLowerCase(Locale.ROOT);
        return type.startsWith("text/") || type.contains("json") || type.contains("xml")
                || type.contains("x-www-form-urlencoded") || type.contains("javascript");
    }

    /**
     * URL client thực sự gọi (qua gateway), dựng từ header X-Forwarded-* mà gateway thêm vào.
     * Giá trị nhạy cảm trong query string bị che.
     */
    private String fullUrl(HttpServletRequest req) {
        String proto = firstValue(req.getHeader("X-Forwarded-Proto"));
        String host = firstValue(req.getHeader("X-Forwarded-Host"));
        String port = firstValue(req.getHeader("X-Forwarded-Port"));
        String prefix = firstValue(req.getHeader("X-Forwarded-Prefix"));
        if (proto == null) {
            proto = req.getScheme();
        }
        if (host == null) {
            host = req.getHeader("Host");
        }
        if (host == null) {
            String serverPort = String.valueOf(req.getServerPort());
            host = isDefaultPort(proto, serverPort) ? req.getServerName() : req.getServerName() + ":" + serverPort;
        } else if (!host.contains(":") && port != null && !isDefaultPort(proto, port)) {
            host = host + ":" + port;
        }
        StringBuilder url = new StringBuilder(proto).append("://").append(host);
        if (prefix != null) {
            url.append(prefix);
        }
        url.append(req.getRequestURI());
        if (req.getQueryString() != null) {
            url.append('?').append(sanitizer.form(req.getQueryString()));
        }
        return url.toString();
    }

    /**
     * Gateway nối IP nó nhìn thấy vào cuối X-Forwarded-For, nên lấy phần tử cuối (client không giả được).
     * Phần tử đầu do client tự gửi lên, không đáng tin. Gọi thẳng vào service (không qua gateway) thì dùng remoteAddr.
     */
    static String clientIp(HttpServletRequest req) {
        String forwardedFor = req.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            String[] parts = forwardedFor.split(",");
            for (int i = parts.length - 1; i >= 0; i--) {
                if (StringUtils.hasText(parts[i])) {
                    return parts[i].trim();
                }
            }
        }
        return req.getRemoteAddr();
    }

    private boolean matches(List<String> patterns, HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return patterns.stream().anyMatch(p -> pathMatcher.match(p, path));
    }

    private static String firstValue(String header) {
        if (!StringUtils.hasText(header)) {
            return null;
        }
        int comma = header.indexOf(',');
        return (comma < 0 ? header : header.substring(0, comma)).trim();
    }

    private static boolean isDefaultPort(String proto, String port) {
        return ("http".equals(proto) && "80".equals(port)) || ("https".equals(proto) && "443".equals(port));
    }
}
