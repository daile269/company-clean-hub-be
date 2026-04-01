package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.response.NotificationResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.entity.Notification;
import com.company.company_clean_hub_be.entity.NotificationType;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.NotificationRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.service.NotificationService;
import com.company.company_clean_hub_be.service.SseEmitterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SseEmitterService sseEmitterService;

    @Override
    @Transactional
    public void createNotification(User recipient,
                                   NotificationType type,
                                   String title,
                                   String message,
                                   Long refEmployeeId,
                                   Long refAssignmentId,
                                   Long refContractId) {
        Notification notification = Notification.builder()
                .recipient(recipient)
                .type(type)
                .title(title)
                .message(message)
                .refEmployeeId(refEmployeeId)
                .refAssignmentId(refAssignmentId)
                .refContractId(refContractId)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
        notificationRepository.save(notification);
        log.info("Notification created: type={}, recipientId={}, title={}", type, recipient.getId(), title);

        // Push real-time qua SSE nếu recipient đang kết nối online
        sseEmitterService.sendToUser(recipient.getId(), mapToResponse(notification));
    }

    @Override
    public List<NotificationResponse> getMyNotifications() {
        Long currentUserId = getCurrentUserId();
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<NotificationResponse> getMyNotificationsWithFilter(String type, Boolean isRead) {
        User currentUser = getCurrentUserEntity();
        Long recipientId = isManagerRole(currentUser) ? null : currentUser.getId();

        // Parse type String → enum (null nếu không truyền hoặc "ALL")
        NotificationType notificationType = null;
        if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type)) {
            try {
                notificationType = NotificationType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
        }
        return notificationRepository.findWithFilters(recipientId, notificationType, isRead)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<NotificationResponse> getMyNotificationsPaged(
            String type, Boolean isRead, int page, int pageSize) {
        User currentUser = getCurrentUserEntity();
        Long recipientId = isManagerRole(currentUser) ? null : currentUser.getId();

        NotificationType notificationType = null;
        if (type != null && !type.isBlank() && !"ALL".equalsIgnoreCase(type)) {
            try {
                notificationType = NotificationType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
        }
        Page<Notification> pageResult = notificationRepository.findWithFiltersPaged(
                recipientId, notificationType, isRead,
                PageRequest.of(page, pageSize)
        );
        List<NotificationResponse> content = pageResult.getContent()
                .stream().map(this::mapToResponse).collect(Collectors.toList());
        return PageResponse.<NotificationResponse>builder()
                .content(content)
                .page(pageResult.getNumber())
                .pageSize(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst())
                .last(pageResult.isLast())
                .build();
    }

    @Override
    public List<NotificationResponse> getMyUnreadNotifications() {
        Long currentUserId = getCurrentUserId();
        return notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(currentUserId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public long countMyUnread() {
        User currentUser = getCurrentUserEntity();
        if (isManagerRole(currentUser)) {
            // Đối với QLT1, đếm tất cả thông báo chưa đọc trong hệ thống
            return notificationRepository.countByIsReadFalse();
        }
        return notificationRepository.countByRecipientIdAndIsReadFalse(currentUser.getId());
    }

    @Override
    @Transactional
    public NotificationResponse getDetail(Long id) {
        Long currentUserId = getCurrentUserId();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        // Chỉ owner mới được xem
        if (!notification.getRecipient().getId().equals(currentUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        // Tự động đánh dấu đã đọc khi xem chi tiết
        if (Boolean.FALSE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notificationRepository.save(notification);
            log.info("Auto mark-as-read notificationId={} for userId={}", id, currentUserId);
        }

        return mapToResponse(notification);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long id) {
        User currentUser = getCurrentUserEntity();
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        // Chỉ owner mới được đánh dấu đã đọc, TRỪ khi là QLT1
        if (!isManagerRole(currentUser) && !notification.getRecipient().getId().equals(currentUser.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        notification.setIsRead(true);
        notificationRepository.save(notification);
        return mapToResponse(notification);
    }

    @Override
    @Transactional
    public void markAllAsRead() {
        User currentUser = getCurrentUserEntity();
        List<Notification> unread;
        
        if (isManagerRole(currentUser)) {
            // QLT1: Đánh dấu ĐÃ ĐỌC tất cả thông báo chưa đọc trong toàn bộ hệ thống
            unread = notificationRepository.findAllByIsReadFalse();
        } else {
            // User thường: Chỉ đánh dấu đống của mình
            unread = notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(currentUser.getId());
        }
        
        unread.forEach(n -> n.setIsRead(true));
        notificationRepository.saveAll(unread);
        log.info("Marked {} notifications as read for role={}", unread.size(), currentUser.getRole().getCode());
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private User getCurrentUserEntity() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
    }

    private Long getCurrentUserId() {
        return getCurrentUserEntity().getId();
    }

    private boolean isManagerRole(User user) {
        return user.getRole() != null && "QLT1".equalsIgnoreCase(user.getRole().getCode());
    }

    private NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .type(n.getType() != null ? n.getType().name() : null)
                .typeDescription(n.getType() != null ? n.getType().getDescription() : null)
                .title(n.getTitle())
                .message(n.getMessage())
                .refEmployeeId(n.getRefEmployeeId())
                .refAssignmentId(n.getRefAssignmentId())
                .refContractId(n.getRefContractId())
                .isRead(n.getIsRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
