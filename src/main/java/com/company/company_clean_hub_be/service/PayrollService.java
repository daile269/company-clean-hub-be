package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.entity.Payroll;
import java.util.List;
import java.util.Optional;

public interface PayrollService {
    List<Payroll> findAll();
    Optional<Payroll> findById(Long id);
    Payroll save(Payroll payroll);
    void deleteById(Long id);
}
