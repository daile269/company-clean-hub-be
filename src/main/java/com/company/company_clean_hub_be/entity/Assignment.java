package com.company.company_clean_hub_be.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "assignments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler" })
public class Assignment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    @NotNull
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private Contract contract; // Nullable for COMPANY scope

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by_id")
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private User assignedBy; // Người phân công assignment này

    @Column(name = "start_date")
    @NotNull
    private LocalDate startDate;

    @Enumerated(EnumType.STRING)
    private AssignmentStatus status;

    @Column(name = "salary_at_time")
    @PositiveOrZero
    private BigDecimal salaryAtTime;

    @Column(name = "work_days")
    @PositiveOrZero
    private Integer workDays;

    @Column(name = "planned_days")
    @PositiveOrZero
    private Integer plannedDays;

    @ElementCollection(targetClass = DayOfWeek.class)
    @CollectionTable(name = "assignment_working_days", joinColumns = @JoinColumn(name = "assignment_id"))
    @Column(name = "day_of_week")
    @Enumerated(EnumType.STRING)
    private List<java.time.DayOfWeek> workingDaysPerWeek;

    @Column(name = "additional_allowance")
    @PositiveOrZero
    private BigDecimal additionalAllowance;
    @OneToMany(mappedBy = "assignment", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private List<Attendance> attendances;
    private String description;

    @Enumerated(EnumType.STRING)
    private AssignmentType assignmentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 20)
    @Builder.Default
    private AssignmentScope scope = AssignmentScope.CONTRACT;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
