package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.NotificationResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.service.NotificationService;
import com.company.company_clean_hub_be.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;
    private final SseEmitterService sseEmitterService;
    private final UserRepository userRepository;

    /**
     * GET /api/notifications/subscribe
     * Kết nối SSE để nhận notification real-time.
     * FE gọi 1 lần khi load app, giữ kết nối và lắng nghe event "notification".
     */
    @GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public SseEmitter subscribe() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
        return sseEmitterService.subscribe(user.getId());
    }

    /**
     * GET /api/notifications?type=ALL&isRead=false&page=0&pageSize=20
     * Lấy notification có filter + phân trang.
     * - type     : ALL | WORK_TIME_CONFLICT | NEW_EMPLOYEE_CREATED (mặc định: ALL)
     * - isRead   : true | false | không truyền = lấy tất cả
     * - page     : số trang, bắt đầu từ 0 (mặc định: 0)
     * - pageSize : số bản ghi mỗi trang (mặc định: 20)
     */
    @GetMapping
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ApiResponse<PageResponse<NotificationResponse>> getMyNotifications(
            @RequestParam(required = false, defaultValue = "ALL") String type,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResponse<NotificationResponse> result = notificationService.getMyNotificationsPaged(type, isRead, page, pageSize);
        return ApiResponse.success("Lấy danh sách thông báo thành công", result, HttpStatus.OK.value());
    }

    /**
     * GET /api/notifications/unread
     * Lấy notification chưa đọc.
     */
    @GetMapping("/unread")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ApiResponse<List<NotificationResponse>> getMyUnreadNotifications() {
        List<NotificationResponse> result = notificationService.getMyUnreadNotifications();
        return ApiResponse.success("Lấy danh sách thông báo chưa đọc thành công", result, HttpStatus.OK.value());
    }

    /**
     * GET /api/notifications/unread/count
     * Đếm số notification chưa đọc (dùng cho badge trên UI).
     */
    @GetMapping("/unread/count")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ApiResponse<Map<String, Long>> countUnread() {
        long count = notificationService.countMyUnread();
        return ApiResponse.success("Đếm số thông báo chưa đọc thành công", Map.of("count", count), HttpStatus.OK.value());
    }

    /**
     * GET /api/notifications/{id}
     * Xem chi tiết 1 notification (và tự động đánh dấu đã đọc).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ApiResponse<NotificationResponse> getNotificationDetail(@PathVariable Long id) {
        NotificationResponse result = notificationService.getDetail(id);
        return ApiResponse.success("Lấy chi tiết thông báo thành công", result, HttpStatus.OK.value());
    }

    /**
     * PUT /api/notifications/{id}/read
     * Đánh dấu 1 notification đã đọc (không redirect, dùng khi FE tự xử lý).
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ApiResponse<NotificationResponse> markAsRead(@PathVariable Long id) {
        NotificationResponse result = notificationService.markAsRead(id);
        return ApiResponse.success("Đã đánh dấu thông báo là đã đọc", result, HttpStatus.OK.value());
    }

    /**
     * PUT /api/notifications/read-all
     * Đánh dấu tất cả notification của user đang login là đã đọc.
     */
    @PutMapping("/read-all")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ApiResponse<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ApiResponse.success("Đã đánh dấu tất cả thông báo là đã đọc", null, HttpStatus.OK.value());
    }
}
