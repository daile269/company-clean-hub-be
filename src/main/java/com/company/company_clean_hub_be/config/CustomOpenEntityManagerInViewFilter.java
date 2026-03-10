package com.company.company_clean_hub_be.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewFilter;
import org.springframework.stereotype.Component;

/**
 * Filter này ghi đè logic mặc định của Spring Boot OSIV (Open Session In View).
 * Mục đích: Chỉ loại trừ duy nhất endpoint Server-Sent Events (SSE) để kết nối DB
 * không bị ngậm vô thời hạn gây treo hệ thống. Các request bình thường khác vẫn xài 
 * OSIV như cũ để hỗ trợ Lazy Load Entity trong Controller.
 */
@Component
public class CustomOpenEntityManagerInViewFilter extends OpenEntityManagerInViewFilter {

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();
        
        // Loại trừ SSE endpoint
        if (path.startsWith("/api/notifications/subscribe")) {
            return true;
        }
        
        return super.shouldNotFilter(request);
    }
}
