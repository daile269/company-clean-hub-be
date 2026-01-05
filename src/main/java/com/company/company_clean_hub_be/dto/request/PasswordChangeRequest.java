package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PasswordChangeRequest {
    @NotBlank(message = "Mật khẩu mới bắt buộc")
    @Size(min = 6, max = 255, message = "Mật khẩu phải có ít nhất 6 ký tự")
    private String newPassword;

    @NotBlank(message = "Xác nhận mật khẩu bắt buộc")
    @Size(min = 6, max = 255, message = "Mật khẩu xác nhận phải có ít nhất 6 ký tự")
    private String confirmPassword;
}
