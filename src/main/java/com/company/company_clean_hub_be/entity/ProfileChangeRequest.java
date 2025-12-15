package com.company.company_clean_hub_be.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity lưu trữ yêu cầu thay đổi thông tin cá nhân từ nhân viên
 */
@Entity
@Table(name = "profile_change_requests")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChangeRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_user_id", nullable = false)
    private User requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ChangeType changeType;

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_user_id")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ChangeType {
        BASIC_INFO,      // Thay đổi thông tin cơ bản (tên, địa chỉ, sdt)
        BANK_INFO,       // Thay đổi thông tin ngân hàng
        CONTACT_INFO,    // Thay đổi thông tin liên lạc
        EMERGENCY_CONTACT, // Thay đổi người liên hệ khẩn cấp
        OTHER            // Khác
    }

    public enum RequestStatus {
        PENDING,   // Chờ duyệt
        APPROVED,  // Đã duyệt
        REJECTED,  // Từ chối
        CANCELLED  // Hủy bỏ
    }
}
