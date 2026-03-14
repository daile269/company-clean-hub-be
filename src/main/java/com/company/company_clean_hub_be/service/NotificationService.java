package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.response.NotificationResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.entity.NotificationType;
import com.company.company_clean_hub_be.entity.User;

import java.util.List;

public interface NotificationService {

    /**
     * Tạo notification cho một user nhận cụ thể (internal).
     */
    void createNotification(User recipient,
                            NotificationType type,
                            String title,
                            String message,
                            Long refEmployeeId,
                            Long refAssignmentId,
                            Long refContractId);

    /**
     * Lấy tất cả notification của user đang login.
     */
    List<NotificationResponse> getMyNotifications();

    /**
     * Lấy notification có filter (type và isRead đều nullable).
     * type = null   → không lọc theo loại
     * isRead = null → lấy cả đã đọc và chưa đọc
     */
    List<NotificationResponse> getMyNotificationsWithFilter(String type, Boolean isRead);

    /**
     * Lấy notification có filter + phân trang.
     * page bắt đầu từ 0, pageSize mặc định 20.
     */
    PageResponse<NotificationResponse> getMyNotificationsPaged(String type, Boolean isRead, int page, int pageSize);

    /**
     * Lấy notification chưa đọc của user đang login.
     */
    List<NotificationResponse> getMyUnreadNotifications();

    /**
     * Đếm số notification chưa đọc (dùng cho badge).
     */
    long countMyUnread();

    /**
     * Xem chi tiết 1 notification — tự động đánh dấu đã đọc nếu chưa đọc.
     * Chỉ owner mới được xem.
     */
    NotificationResponse getDetail(Long id);

    /**
     * Đánh dấu 1 notification đã đọc.
     */
    NotificationResponse markAsRead(Long id);

    /**
     * Đánh dấu tất cả notification của user đang login là đã đọc.
     */
    void markAllAsRead();
}
