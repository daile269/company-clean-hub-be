package com.company.company_clean_hub_be.controller;

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
    public ResponseEntity<PageResponse<NotificationResponse>> getMyNotifications(
            @RequestParam(required = false, defaultValue = "ALL") String type,
            @RequestParam(required = false) Boolean isRead,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ResponseEntity.ok(notificationService.getMyNotificationsPaged(type, isRead, page, pageSize));
    }

    /**
     * GET /api/notifications/unread
     * Lấy notification chưa đọc.
     */
    @GetMapping("/unread")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ResponseEntity<List<NotificationResponse>> getMyUnreadNotifications() {
        return ResponseEntity.ok(notificationService.getMyUnreadNotifications());
    }

    /**
     * GET /api/notifications/unread/count
     * Đếm số notification chưa đọc (dùng cho badge trên UI).
     */
    @GetMapping("/unread/count")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ResponseEntity<Map<String, Long>> countUnread() {
        long count = notificationService.countMyUnread();
        return ResponseEntity.ok(Map.of("count", count));
    }

    /**
     * GET /api/notifications/{id}
     * Xem chi tiết 1 notification (và tự động đánh dấu đã đọc).
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ResponseEntity<NotificationResponse> getNotificationDetail(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getDetail(id));
    }

    /**
     * PUT /api/notifications/{id}/read
     * Đánh dấu 1 notification đã đọc (không redirect, dùng khi FE tự xử lý).
     */
    @PutMapping("/{id}/read")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ResponseEntity<NotificationResponse> markAsRead(@PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(id));
    }

    /**
     * PUT /api/notifications/read-all
     * Đánh dấu tất cả notification của user đang login là đã đọc.
     */
    @PutMapping("/read-all")
    @PreAuthorize("hasAuthority('NOTIFICATION_VIEW')")
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok().build();
    }
}
