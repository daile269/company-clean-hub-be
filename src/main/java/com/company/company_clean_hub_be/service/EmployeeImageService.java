package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.entity.EmployeeImage;
import java.util.List;
import java.util.Optional;

public interface EmployeeImageService {
    List<EmployeeImage> findAll();
    Optional<EmployeeImage> findById(Long id);
    EmployeeImage save(EmployeeImage image);
    void deleteById(Long id);
}
