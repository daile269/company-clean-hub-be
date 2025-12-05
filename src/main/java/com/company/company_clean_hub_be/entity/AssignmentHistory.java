package com.company.company_clean_hub_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "assignment_history")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AssignmentHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    // Assignment cũ (bị inactive)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "old_assignment_id")
    Assignment oldAssignment;

    // Assignment mới (được tạo)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "new_assignment_id")
    Assignment newAssignment;

    // Thông tin nhân viên bị thay
    @Column(name = "replaced_employee_id")
    Long replacedEmployeeId;

    @Column(name = "replaced_employee_name")
    String replacedEmployeeName;

    // Thông tin nhân viên thay thế
    @Column(name = "replacement_employee_id")
    Long replacementEmployeeId;

    @Column(name = "replacement_employee_name")
    String replacementEmployeeName;

    // Thông tin hợp đồng
    @Column(name = "contract_id")
    Long contractId;

    @Column(name = "customer_name")
    String customerName;

    // Các ngày điều động
    @ElementCollection
    @CollectionTable(name = "assignment_history_dates", joinColumns = @JoinColumn(name = "history_id"))
    @Column(name = "reassignment_date")
    List<LocalDate> reassignmentDates;

    // Loại điều động
    @Enumerated(EnumType.STRING)
    @Column(name = "reassignment_type")
    ReassignmentType reassignmentType;

    // Ghi chú/lý do
    @Column(length = 1000)
    String notes;

    // Trạng thái (để biết có thể rollback không)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    HistoryStatus status = HistoryStatus.ACTIVE;

    // Người thực hiện điều động
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    User createdBy;

    @Column(name = "created_at")
    LocalDateTime createdAt;

    // Người rollback (nếu có)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rollback_by")
    User rollbackBy;

    @Column(name = "rollback_at")
    LocalDateTime rollbackAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
