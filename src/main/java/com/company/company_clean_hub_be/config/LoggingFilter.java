package com.company.company_clean_hub_be.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
public class LoggingFilter extends OncePerRequestFilter {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        ContentCachingRequestWrapper wrappedRequest = new ContentCachingRequestWrapper(request);
        ContentCachingResponseWrapper wrappedResponse = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logRequestResponse(wrappedRequest, wrappedResponse, duration);
            wrappedResponse.copyBodyToResponse();
        }
    }

    private void logRequestResponse(ContentCachingRequestWrapper request, 
                                     ContentCachingResponseWrapper response, 
                                     long duration) {
        String username = getUsername();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        int status = response.getStatus();
        String timestamp = LocalDateTime.now().format(formatter);

        // Short single-line log: timestamp user method uri?qs status duration [body]
        StringBuilder single = new StringBuilder();
          single.append(timestamp).append(" |")
              .append("username:").append(username).append(" |")
              .append(method).append(" |")
              .append(uri);
        if (queryString != null) {
            single.append("?").append(queryString);
        }
        single.append(" |").append(status)
              .append(" |").append(duration).append("ms");

        // Optionally include small request body for write operations (truncated)
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            String requestBody = getContentAsString(request.getContentAsByteArray(), request.getCharacterEncoding());
            if (requestBody != null && !requestBody.isEmpty()) {
                requestBody = maskSensitiveData(requestBody);
                if (requestBody.length() > 200) {
                    requestBody = requestBody.substring(0, 200) + "...";
                }
                single.append(" |").append(requestBody);
            }
        }

        // Log with appropriate level
        if (status >= 500) {
            log.error(single.toString());
        } else if (status >= 400) {
            log.warn(single.toString());
        } else {
            log.info(single.toString());
        }
    }

    private String getUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() 
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getName();
        }
        return "Anonymous";
    }

    private String getContentAsString(byte[] content, String encoding) {
        try {
            return new String(content, encoding != null ? encoding : "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return "[Error reading content]";
        }
    }

    private String maskSensitiveData(String content) {
        // Ẩn password, token, secret
        return content
                .replaceAll("(\"password\"\\s*:\\s*\")[^\"]*", "$1***")
                .replaceAll("(\"token\"\\s*:\\s*\")[^\"]*", "$1***")
                .replaceAll("(\"secret\"\\s*:\\s*\")[^\"]*", "$1***")
                .replaceAll("(\"oldPassword\"\\s*:\\s*\")[^\"]*", "$1***")
                .replaceAll("(\"newPassword\"\\s*:\\s*\")[^\"]*", "$1***");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Không log các endpoint tĩnh
        String path = request.getRequestURI();
        return path.startsWith("/actuator") 
                || path.startsWith("/swagger-ui") 
                || path.startsWith("/v3/api-docs")
                || path.endsWith(".css")
                || path.endsWith(".js")
                || path.endsWith(".png")
                || path.endsWith(".jpg")
                || path.endsWith(".ico");
    }
}
