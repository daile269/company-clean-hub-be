package com.company.company_clean_hub_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.company_clean_hub_be.entity.Payroll;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {
}
