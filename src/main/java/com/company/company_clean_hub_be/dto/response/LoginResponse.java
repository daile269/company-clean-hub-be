package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {
    private String token;
    private String type = "Bearer";
    private Long id;
    private String username;
    private String email;
    private String phone;
    private String roleName;
    private Long roleId;
    private String userType;

    public LoginResponse(String token, Long id, String username, String email, String phone, String roleName, Long roleId, String userType) {
        this.token = token;
        this.type = "Bearer";
        this.id = id;
        this.username = username;
        this.email = email;
        this.phone = phone;
        this.roleName = roleName;
        this.roleId = roleId;
        this.userType = userType;
    }
}
