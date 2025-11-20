package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.entity.Role;
import java.util.List;
import java.util.Optional;

public interface RoleService {
    List<Role> findAll();
    Optional<Role> findById(Long id);
    Role save(Role role);
    void deleteById(Long id);
}
