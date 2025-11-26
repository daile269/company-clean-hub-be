package com.company.company_clean_hub_be.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.company.company_clean_hub_be.entity.EmployeeImage;

public interface EmployeeImageRepository extends JpaRepository<EmployeeImage, Long> {
	java.util.List<EmployeeImage> findByEmployeeId(Long employeeId);
}
