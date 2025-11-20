package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.entity.Assignment;
import java.util.List;
import java.util.Optional;

public interface AssignmentService {
    List<Assignment> findAll();
    Optional<Assignment> findById(Long id);
    Assignment save(Assignment assignment);
    void deleteById(Long id);
}
