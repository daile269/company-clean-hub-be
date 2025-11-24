package com.company.company_clean_hub_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.company.company_clean_hub_be.entity.Assignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    
    @Query("SELECT a FROM Assignment a " +
           "LEFT JOIN a.employee e " +
           "LEFT JOIN a.customer c " +
           "WHERE (:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Assignment> findByFilters(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
