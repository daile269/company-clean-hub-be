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
public class RoleRequest {

    @NotBlank(message = "Tên vai trò không được để trống")
    @Size(max = 100, message = "Tên vai trò không được vượt quá 100 ký tự")
    private String name;

    @Size(max = 50, message = "Mã vai trò không được vượt quá 50 ký tự")
    private String code;

    @Size(max = 255, message = "Mô tả không được vượt quá 255 ký tự")
    private String description;
}
