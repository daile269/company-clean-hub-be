package com.company.company_clean_hub_be.dto.request;

import com.company.company_clean_hub_be.entity.EmploymentType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeRequest {
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Tên đăng nhập phải có độ dài từ 3 đến 50 ký tự")
    private String username;

    @NotBlank(message = "Mã nhân viên bắt buộc")
    @Size(max = 50, message = "Mã nhân viên không được vượt quá 50 ký tự")
    private String employeeCode;

    @NotBlank(message = "Mật khẩu bắt buộc")
    @Size(min = 6, max = 255, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String password;

    @Size(max = 50, message = "Số điện thoại không được vượt quá 50 ký tự")
    private String phone;

    @NotNull(message = "Role ID bắt buộc")
    private Long roleId;

    private String status;

    @NotBlank(message = "CCCD bắt buộc")
    @Size(max = 50, message = "CCCD không được vượt quá 50 ký tự")
    private String cccd;

    private String address;

    @NotBlank(message = "Tên nhân viên bắt buộc")
    @Size(max = 150, message = "Tên không được vượt quá 150 ký tự")
    private String name;

    private String bankAccount;

    private String bankName;

    private String description;

    @NotNull(message = "Loại nhân viên bắt buộc")
    @Builder.Default
    private EmploymentType employmentType = EmploymentType.CONTRACT_STAFF;

    // COMPANY_STAFF fields
    private BigDecimal monthlySalary;

    private BigDecimal allowance;

    private BigDecimal insuranceSalary;

}
