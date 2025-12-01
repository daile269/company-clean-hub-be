package com.company.company_clean_hub_be.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.company_clean_hub_be.entity.Payroll;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {
    
    @Query("SELECT DISTINCT p FROM Payroll p " +
            "JOIN p.attendances a " +
            "JOIN a.assignment asn " +
            "WHERE asn.employee.id = :employeeId " +
            "AND MONTH(p.createdAt) = :month AND YEAR(p.createdAt) = :year")
    Optional<Payroll> findByEmployeeAndMonthAndYear(
            @Param("employeeId") Long employeeId,
            @Param("month") Integer month,
            @Param("year") Integer year
    );
    
    @Query("SELECT DISTINCT p FROM Payroll p " +
            "LEFT JOIN p.attendances a " +
            "LEFT JOIN a.assignment asn " +
            "LEFT JOIN asn.employee e " +
            "WHERE (:keyword IS NULL OR :keyword = '' OR " +
            "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
            "AND (:month IS NULL OR MONTH(p.createdAt) = :month) " +
            "AND (:year IS NULL OR YEAR(p.createdAt) = :year) " +
            "AND (:isPaid IS NULL OR p.isPaid = :isPaid)")
    Page<Payroll> findByFilters(
            @Param("keyword") String keyword,
            @Param("month") Integer month,
            @Param("year") Integer year,
            @Param("isPaid") Boolean isPaid,
            Pageable pageable
    );
}
