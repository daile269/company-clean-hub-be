package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.Notification;
import com.company.company_clean_hub_be.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Lấy tất cả (mới nhất trước)
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

    // Lấy chưa đọc
    List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(Long recipientId);

    // Đếm chưa đọc (badge)
    long countByRecipientIdAndIsReadFalse(Long recipientId);

    // Đếm tất cả chưa đọc trong toàn bộ hệ thống
    long countByIsReadFalse();

    // Lấy tất cả chưa đọc trong toàn bộ hệ thống
    List<Notification> findAllByIsReadFalse();

    // ─── Filter theo type ───────────────────────────────────────────────────────

    // Lấy tất cả theo type
    List<Notification> findByRecipientIdAndTypeOrderByCreatedAtDesc(Long recipientId, NotificationType type);

    // Lấy chưa đọc theo type
    List<Notification> findByRecipientIdAndTypeAndIsReadFalseOrderByCreatedAtDesc(Long recipientId, NotificationType type);

    // Lấy đã đọc theo type
    List<Notification> findByRecipientIdAndTypeAndIsReadTrueOrderByCreatedAtDesc(Long recipientId, NotificationType type);

    // Lấy đã đọc (không lọc type)
    List<Notification> findByRecipientIdAndIsReadTrueOrderByCreatedAtDesc(Long recipientId);

    // ─── Query linh hoạt (type nullable = lấy tất cả) ───────────────────────────
    @Query("""
        SELECT n FROM Notification n
        WHERE (:recipientId IS NULL OR n.recipient.id = :recipientId)
        AND (:type IS NULL OR n.type = :type)
        AND (:isRead IS NULL OR n.isRead = :isRead)
        ORDER BY n.createdAt DESC
    """)
    List<Notification> findWithFilters(
            @Param("recipientId") Long recipientId,
            @Param("type") NotificationType type,
            @Param("isRead") Boolean isRead
    );

    // ─── Query có phân trang ─────────────────────────────────────────────────────
    @Query("""
        SELECT n FROM Notification n
        WHERE (:recipientId IS NULL OR n.recipient.id = :recipientId)
        AND (:type IS NULL OR n.type = :type)
        AND (:isRead IS NULL OR n.isRead = :isRead)
        ORDER BY n.createdAt DESC
    """)
    Page<Notification> findWithFiltersPaged(
            @Param("recipientId") Long recipientId,
            @Param("type") NotificationType type,
            @Param("isRead") Boolean isRead,
            Pageable pageable
    );

    void deleteByCreatedAtBefore(LocalDateTime cutoff);
}
