package com.company.company_clean_hub_be.entity;

import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "deleted_attendance_backup")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeletedAttendanceBackup {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_attendance_id")
    private Long originalAttendanceId;

    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "employee_id")
    private Long employeeId;

    @Column(name = "date")
    private LocalDate date;

    @Column(name = "work_hours", precision = 10, scale = 2)
    private BigDecimal workHours;

    @Column(name = "bonus", precision = 18, scale = 2)
    private BigDecimal bonus;

    @Column(name = "penalty", precision = 18, scale = 2)
    private BigDecimal penalty;

    @Column(name = "support_cost", precision = 18, scale = 2)
    private BigDecimal supportCost;

    @Column(name = "is_overtime")
    private Boolean isOvertime;

    @Column(name = "overtime_amount", precision = 18, scale = 2)
    private BigDecimal overtimeAmount;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "deleted_by")
    private String deletedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Lob
    @Column(name = "payload", columnDefinition = "LONGTEXT")
    private String payload;
}
