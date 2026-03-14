package com.company.company_clean_hub_be.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Người nhận thông báo (Quản lý tổng - QLT)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private User recipient;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 50, nullable = false)
    private NotificationType type;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    // Refs để frontend điều hướng (nullable)
    @Column(name = "ref_employee_id")
    private Long refEmployeeId;

    @Column(name = "ref_assignment_id")
    private Long refAssignmentId;

    @Column(name = "ref_contract_id")
    private Long refContractId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
