package com.company.company_clean_hub_be.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.company_clean_hub_be.entity.Payroll;

public interface PayrollRepository extends JpaRepository<Payroll, Long> {

        @Query("SELECT p FROM Payroll p " +
                        "WHERE p.employee.id = :employeeId " +
                        "AND MONTH(p.createdAt) = :month AND YEAR(p.createdAt) = :year")
        Optional<Payroll> findByEmployeeAndMonthAndYear(
                        @Param("employeeId") Long employeeId,
                        @Param("month") Integer month,
                        @Param("year") Integer year);

        @Query("SELECT p FROM Payroll p " +
                        "LEFT JOIN p.employee e " +
                        "WHERE (:keyword IS NULL OR :keyword = '' OR " +
                        "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                        "AND (:month IS NULL OR MONTH(p.createdAt) = :month) " +
                        "AND (:year IS NULL OR YEAR(p.createdAt) = :year) " +
                        "AND (:isPaid IS NULL OR " +
                        "(:isPaid = true AND p.status = com.company.company_clean_hub_be.entity.PayrollStatus.PAID) OR "
                        +
                        "(:isPaid = false AND p.status IN (com.company.company_clean_hub_be.entity.PayrollStatus.UNPAID, com.company.company_clean_hub_be.entity.PayrollStatus.PARTIAL_PAID)))")
        Page<Payroll> findByFilters(
                        @Param("keyword") String keyword,
                        @Param("month") Integer month,
                        @Param("year") Integer year,
                        @Param("isPaid") Boolean isPaid,
                        Pageable pageable);

        @Query("SELECT COUNT(p), " +
                        "SUM(CASE WHEN p.status = com.company.company_clean_hub_be.entity.PayrollStatus.PAID THEN 1 ELSE 0 END), " +
                        "SUM(CASE WHEN p.status = com.company.company_clean_hub_be.entity.PayrollStatus.UNPAID THEN 1 ELSE 0 END), " +
                        "SUM(CASE WHEN p.status = com.company.company_clean_hub_be.entity.PayrollStatus.PARTIAL_PAID THEN 1 ELSE 0 END), " +
                        "COALESCE(SUM(p.finalSalary), 0), " +
                        "COALESCE(SUM(COALESCE(p.paidAmount, 0)), 0) " +
                        "FROM Payroll p " +
                        "LEFT JOIN p.employee e " +
                        "WHERE (:keyword IS NULL OR :keyword = '' OR " +
                        "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                        "AND (:month IS NULL OR MONTH(p.createdAt) = :month) " +
                        "AND (:year IS NULL OR YEAR(p.createdAt) = :year) " +
                        "AND (:isPaid IS NULL OR " +
                        "(:isPaid = true AND p.status = com.company.company_clean_hub_be.entity.PayrollStatus.PAID) OR " +
                        "(:isPaid = false AND p.status IN (com.company.company_clean_hub_be.entity.PayrollStatus.UNPAID, com.company.company_clean_hub_be.entity.PayrollStatus.PARTIAL_PAID)))")
        Object[] getOverviewStats(
                        @Param("keyword") String keyword,
                        @Param("month") Integer month,
                        @Param("year") Integer year,
                        @Param("isPaid") Boolean isPaid);

        @Query("SELECT DISTINCT YEAR(p.createdAt) FROM Payroll p ORDER BY YEAR(p.createdAt) DESC")
        List<Integer> getDistinctYears();
}
