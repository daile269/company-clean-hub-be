package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponse {
    private Long id;
    private String username;
    private String phone;
    private String email;
    private Long roleId;
    private String roleName;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
