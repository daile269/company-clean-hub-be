package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.RoleRequest;
import com.company.company_clean_hub_be.dto.response.RoleResponse;
import java.util.List;

public interface RoleService {
    List<RoleResponse> getAllRoles();
    RoleResponse getRoleById(Long id);
    RoleResponse createRole(RoleRequest request);
    RoleResponse updateRole(Long id, RoleRequest request);
    void deleteRole(Long id);
}
