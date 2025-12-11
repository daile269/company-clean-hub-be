package com.company.company_clean_hub_be.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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

    private String bankAccount;

    @Column(name = "bank_name")
    private String bankName;

    private String description;

    // Loại nhân viên: COMPANY_STAFF (văn phòng) hoặc CONTRACT_STAFF (hợp đồng)
    @Enumerated(EnumType.STRING)
    @Column(name = "employment_type", nullable = false, length = 20)
    private EmploymentType employmentType = EmploymentType.CONTRACT_STAFF;

    // Chỉ cho COMPANY_STAFF: Lương cố định tháng
    @Column(name = "monthly_salary", precision = 18, scale = 2)
    private BigDecimal monthlySalary;

    // Chỉ cho COMPANY_STAFF: Phụ cấp
    @Column(name = "allowance", precision = 18, scale = 2)
    private BigDecimal allowance;

    // Chỉ cho COMPANY_STAFF: Mức lương đóng BHXH/BHYT
    @Column(name = "insurance_salary", precision = 18, scale = 2)
    private BigDecimal insuranceSalary;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
