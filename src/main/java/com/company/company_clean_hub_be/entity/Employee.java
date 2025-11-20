package com.company.company_clean_hub_be.entity;

import java.time.LocalDateTime;

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
public class Employee extends User {
    @NotBlank
    @Size(max = 50)
    private String cccd;

    @Size(max = 255)
    private String address;

    @NotBlank
    @Size(max = 150)
    private String name;
    @Column(name = "bank_account")
    private String bankAccount;

    @Column(name = "bank_name")
    private String bankName;

    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type")
    @NotNull
    private EmploymentType employmentType;

    @Column(name = "base_salary")
    @PositiveOrZero
    private Double baseSalary;

    @Column(name = "daily_salary")
    @PositiveOrZero
    private Double dailySalary;

    @Column(name = "insurance_bhxh")
    @PositiveOrZero
    private Double insuranceBhxh;

    @Column(name = "insurance_bhyt")
    @PositiveOrZero
    private Double insuranceBhyt;

    @Column(name = "insurance_bhtn")
    @PositiveOrZero
    private Double insuranceBhtn;

    @PositiveOrZero
    private Double allowance;

    private String description;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
