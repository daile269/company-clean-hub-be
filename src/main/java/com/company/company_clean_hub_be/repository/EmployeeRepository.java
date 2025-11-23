package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.EmploymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.company_clean_hub_be.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    @Query("SELECT e FROM Employee e WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(e.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
           "(:employmentType IS NULL OR e.employmentType = :employmentType)")
    Page<Employee> findByFilters(
            @Param("keyword") String keyword,
            @Param("employmentType") EmploymentType employmentType,
            Pageable pageable
    );
}
