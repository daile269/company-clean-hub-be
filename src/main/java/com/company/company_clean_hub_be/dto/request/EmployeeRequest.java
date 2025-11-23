package com.company.company_clean_hub_be.dto.request;

import java.math.BigDecimal;

import com.company.company_clean_hub_be.entity.EmploymentType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    @Email(message = "Email không hợp lệ")
    @Size(max = 255, message = "Email không được vượt quá 255 ký tự")
    private String email;

    @NotNull(message = "Role ID bắt buộc")
    private Long roleId;

    private String status;

    @NotBlank(message = "CCCD bắt buộc")
    @Size(max = 50, message = "CCCD không được vượt quá 50 ký tự")
    private String cccd;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @NotBlank(message = "Tên nhân viên bắt buộc")
    @Size(max = 150, message = "Tên không được vượt quá 150 ký tự")
    private String name;

    private String bankAccount;

    private String bankName;

    @NotNull(message = "Loại hình tuyển dụng bắt buộc")
    private EmploymentType employmentType;

    @PositiveOrZero(message = "Lương cơ bản phải là số lớn hơn hoặc bằng 0")
    private BigDecimal baseSalary;

    @PositiveOrZero(message = "Lương theo ngày phải là số lớn hơn hoặc bằng 0")
    private BigDecimal dailySalary;

    @PositiveOrZero(message = "Bảo hiểm xã hội phải là số lớn hơn hoặc bằng 0")
    private BigDecimal socialInsurance;

    @PositiveOrZero(message = "Bảo hiểm y tế phải là số lớn hơn hoặc bằng 0")
    private BigDecimal healthInsurance;

    @PositiveOrZero(message = "Phụ cấp phải là số lớn hơn hoặc bằng 0")
    private BigDecimal allowance;

    private String description;
}
