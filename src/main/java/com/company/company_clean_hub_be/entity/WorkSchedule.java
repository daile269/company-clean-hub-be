package com.company.company_clean_hub_be.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "work_schedules")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class WorkSchedule {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id", nullable = false)
    @NotNull
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Assignment assignment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    @NotNull
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Employee employee;

    @Column(name = "scheduled_date", nullable = false)
    @NotNull
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private WorkScheduleStatus status = WorkScheduleStatus.SCHEDULED;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 30)
    private WorkScheduleReason reason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_verification_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private AssignmentVerification assignmentVerification;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verification_image_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private VerificationImage verificationImage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attendance_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Attendance attendance;

    @Column(name = "photo_captured_at")
    private LocalDateTime photoCapturedAt;

    @Column(name = "attendance_deleted")
    @Builder.Default
    private Boolean attendanceDeleted = false;

    @Column(name = "sync_note", length = 1000)
    private String syncNote;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = WorkScheduleStatus.SCHEDULED;
        if (attendanceDeleted == null) attendanceDeleted = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Helper methods
    public boolean canCapturePhoto() {
        return status == WorkScheduleStatus.SCHEDULED && 
               scheduledDate.equals(LocalDate.now());
    }

    public boolean hasAttendance() {
        return attendance != null && !attendanceDeleted;
    }
}
