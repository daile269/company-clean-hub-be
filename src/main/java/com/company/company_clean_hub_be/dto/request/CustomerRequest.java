package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerRequest {
    @NotBlank(message = "Tên đăng nhập bắt buộc")
    @Size(min = 3, max = 50, message = "Tên đăng nhập phải có độ dài từ 3 đến 50 ký tự")
    private String username;

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

    @NotBlank(message = "Mã khách hàng bắt buộc")
    @Size(max = 50, message = "Mã khách hàng không được vượt quá 50 ký tự")
    private String customerCode;

    @NotBlank(message = "Tên khách hàng bắt buộc")
    @Size(max = 150, message = "Tên không được vượt quá 150 ký tự")
    private String name;

    @Size(max = 255, message = "Địa chỉ không được vượt quá 255 ký tự")
    private String address;

    @Size(max = 255, message = "Thông tin liên hệ không được vượt quá 255 ký tự")
    private String contactInfo;

    @Size(max = 100, message = "Mã số thuế không được vượt quá 100 ký tự")
    private String taxCode;

    @Size(max = 1000, message = "Mô tả không được vượt quá 1000 ký tự")
    private String description;

    @Size(max = 255, message = "Tên công ty không được vượt quá 255 ký tự")
    private String company;
}
