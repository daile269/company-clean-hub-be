package com.company.company_clean_hub_be.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "employees")
@PrimaryKeyJoinColumn(name = "id")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@SuperBuilder
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Employee extends User {

    @NotBlank
    @Size(max = 50)
    @Column(name = "employee_code", unique = true)
    private String employeeCode;

    @NotBlank
    @Size(max = 50)
    @Column(unique = true)
    private String cccd;

    @Size(max = 255)
    private String address;

    @NotBlank
    @Size(max = 150)
    private String name;
    
    @Column(name = "bank_account", unique = true)
    private String bankAccount;

    @Column(name = "bank_name")
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type")
    @NotNull
    private EmploymentType employmentType;

    @Column(name = "base_salary")
    @PositiveOrZero
    private BigDecimal baseSalary;

    @Column(name = "daily_salary")
    @PositiveOrZero
    private BigDecimal dailySalary;

    @Column(name = "social_insurance")
    @PositiveOrZero
    private BigDecimal socialInsurance;

    @Column(name = "health_insurance")
    @PositiveOrZero
    private BigDecimal healthInsurance;

    @PositiveOrZero
    private BigDecimal allowance;

    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
