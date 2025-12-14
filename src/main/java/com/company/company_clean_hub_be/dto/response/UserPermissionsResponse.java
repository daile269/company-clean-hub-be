package com.company.company_clean_hub_be.dto.response;

import com.company.company_clean_hub_be.entity.Permission;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPermissionsResponse {
    private Long userId;
    private String username;
    private String roleCode;
    private String roleName;
    private Set<Permission> permissions;
}
