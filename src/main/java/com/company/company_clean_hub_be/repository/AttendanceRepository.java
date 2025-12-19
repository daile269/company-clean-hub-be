package com.company.company_clean_hub_be.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.company_clean_hub_be.entity.Attendance;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {

        @Query("SELECT a FROM Attendance a WHERE a.assignment.employee.id = :employeeId " +
                        "AND a.date BETWEEN :startDate AND :endDate AND (a.deleted IS NULL OR a.deleted = false)")
        List<Attendance> findByEmployeeAndDateBetween(
                        @Param("employeeId") Long employeeId,
                        @Param("startDate") LocalDate startDate,
                        @Param("endDate") LocalDate endDate);

        @Query("SELECT a FROM Attendance a WHERE a.assignment.employee.id = :employeeId AND a.date = :date AND (a.deleted IS NULL OR a.deleted = false)")
        Optional<Attendance> findByEmployeeAndDate(
                        @Param("employeeId") Long employeeId,
                        @Param("date") LocalDate date);

        @Query("SELECT a FROM Attendance a WHERE a.assignment.employee.id = :employeeId AND a.date = :date AND (a.deleted IS NULL OR a.deleted = false)")
        List<Attendance> findAllByEmployeeAndDate(
                        @Param("employeeId") Long employeeId,
                        @Param("date") LocalDate date);

        @Query("SELECT a FROM Attendance a WHERE a.assignment.id = :assignmentId AND a.assignment.employee.id = :employeeId AND a.date = :date AND (a.deleted IS NULL OR a.deleted = false)")
        Optional<Attendance> findByAssignmentAndEmployeeAndDate(
                        @Param("assignmentId") Long assignmentId,
                        @Param("employeeId") Long employeeId,
                        @Param("date") LocalDate date);

        @Query("SELECT a FROM Attendance a JOIN a.assignment as asn WHERE asn.contract.id = :contractId AND asn.employee.id = :employeeId AND a.date = :date")
        Optional<Attendance> findByContractAndEmployeeAndDate(
                @Param("contractId") Long contractId,
                @Param("employeeId") Long employeeId,
                @Param("date") LocalDate date);

        @Query("SELECT a FROM Attendance a " +
                        "LEFT JOIN a.assignment asn " +
                        "LEFT JOIN asn.employee e " +
                        "LEFT JOIN asn.contract.customer c " +
                        "WHERE (a.deleted IS NULL OR a.deleted = false) " +
                        "AND (:keyword IS NULL OR :keyword = '' OR " +
                        "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
                        "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
                        "AND (:month IS NULL OR MONTH(a.date) = :month) " +
                        "AND (:year IS NULL OR YEAR(a.date) = :year)")
        Page<Attendance> findByFilters(
                        @Param("keyword") String keyword,
                        @Param("month") Integer month,
                        @Param("year") Integer year,
                        Pageable pageable);

        @Query("SELECT a FROM Attendance a " +
                        "WHERE a.assignment.employee.id = :employeeId " +
                        "AND (a.deleted IS NULL OR a.deleted = false) " +
                        "AND (:month IS NULL OR MONTH(a.date) = :month) " +
                        "AND (:year IS NULL OR YEAR(a.date) = :year) " +
                        "ORDER BY a.date DESC")
        Page<Attendance> findByEmployeeAndFilters(
                        @Param("employeeId") Long employeeId,
                        @Param("month") Integer month,
                        @Param("year") Integer year,
                        Pageable pageable);

        @Query("SELECT SUM(a.bonus) FROM Attendance a WHERE a.assignment.id = :assignmentId AND (a.deleted IS NULL OR a.deleted = false)")
        BigDecimal sumBonusByAssignment(@Param("assignmentId") Long assignmentId);

        @Query("SELECT SUM(a.penalty) FROM Attendance a WHERE a.assignment.id = :assignmentId AND (a.deleted IS NULL OR a.deleted = false)")
        BigDecimal sumPenaltyByAssignment(@Param("assignmentId") Long assignmentId);

        @Query("SELECT SUM(a.supportCost) FROM Attendance a WHERE a.assignment.id = :assignmentId AND (a.deleted IS NULL OR a.deleted = false)")
        BigDecimal sumSupportCostByAssignment(@Param("assignmentId") Long assignmentId);

        @Query("SELECT a FROM Attendance a " +
                        "WHERE a.assignment.employee.id = :employeeId " +
                        "AND (a.deleted IS NULL OR a.deleted = false) " +
                        "AND FUNCTION('MONTH', a.date) = :month " +
                        "AND FUNCTION('YEAR', a.date) = :year")
        List<Attendance> findAttendancesByMonthYearAndEmployee(
                        @Param("month") Integer month,
                        @Param("year") Integer year,
                        @Param("employeeId") Long employeeId);

        @Query("SELECT a FROM Attendance a WHERE a.payroll.id = :payrollId AND (a.deleted IS NULL OR a.deleted = false)")
        List<Attendance> findByPayrollId(@Param("payrollId") Long payrollId);

        @Query("SELECT a FROM Attendance a " +
                        "WHERE a.assignment.id = :assignmentId " +
                        "AND (a.deleted IS NULL OR a.deleted = false) " +
                        "AND (:month IS NULL OR MONTH(a.date) = :month) " +
                        "AND (:year IS NULL OR YEAR(a.date) = :year) " +
                        "ORDER BY a.date DESC")
        Page<Attendance> findByAssignmentAndFilters(
                        @Param("assignmentId") Long assignmentId,
                        @Param("month") Integer month,
                        @Param("year") Integer year,
                        Pageable pageable);

        @Query("SELECT a FROM Attendance a WHERE a.assignment.id = :assignmentId AND a.date BETWEEN :startDate AND :endDate AND (a.deleted IS NULL OR a.deleted = false)")
        List<Attendance> findByAssignmentAndDateBetween(
                        @Param("assignmentId") Long assignmentId,
                        @Param("startDate") java.time.LocalDate startDate,
                        @Param("endDate") java.time.LocalDate endDate);

        

        @Query("SELECT a FROM Attendance a " +
                        "JOIN a.assignment as asn " +
                        "WHERE (a.deleted IS NULL OR a.deleted = false) " +
                        "AND (:month IS NULL OR FUNCTION('MONTH', a.date) = :month) " +
                        "AND (:year IS NULL OR FUNCTION('YEAR', a.date) = :year) " +
                        "AND asn.contract.id IS NOT NULL " +
                        "ORDER BY asn.contract.id, asn.employee.id, a.date")
        List<Attendance> findByMonthYearOrderByContractEmployeeDate(
                        @Param("month") Integer month,
                        @Param("year") Integer year);

        @Query("SELECT a FROM Attendance a " +
                        "JOIN a.assignment as asn " +
                        "WHERE asn.contract.id = :contractId " +
                        "AND (a.deleted IS NULL OR a.deleted = false) " +
                        "AND (:month IS NULL OR FUNCTION('MONTH', a.date) = :month) " +
                        "AND (:year IS NULL OR FUNCTION('YEAR', a.date) = :year) " +
                        "ORDER BY asn.employee.id, a.date")
        List<Attendance> findByContractAndMonthYearOrderByEmployeeDate(
                        @Param("contractId") Long contractId,
                        @Param("month") Integer month,
                        @Param("year") Integer year);

            @Query("SELECT COUNT(a) FROM Attendance a " +
                    "JOIN a.assignment as asn " +
                    "WHERE asn.contract.id = :contractId " +
                    "AND (a.deleted IS NULL OR a.deleted = false) " +
                    "AND (:month IS NULL OR FUNCTION('MONTH', a.date) = :month) " +
                    "AND (:year IS NULL OR FUNCTION('YEAR', a.date) = :year)")
            Long countAttendancesByContractAndMonthYear(
                    @Param("contractId") Long contractId,
                    @Param("month") Integer month,
                    @Param("year") Integer year
            );

        @Query("SELECT a FROM Attendance a WHERE (a.deleted IS NULL OR a.deleted = false)")
        List<Attendance> findAllActive();

        @Query("SELECT COUNT(a) FROM Attendance a " +
                "WHERE a.assignment.id = :assignmentId " +
                "AND (a.deleted IS NULL OR a.deleted = false) " +
                "AND FUNCTION('MONTH', a.date) = :month " +
                "AND FUNCTION('YEAR', a.date) = :year")
        Long countActiveAttendancesByAssignmentAndMonthYear(
                @Param("assignmentId") Long assignmentId,
                @Param("month") Integer month,
                @Param("year") Integer year
        );

        @Query("SELECT a FROM Attendance a " +
                "LEFT JOIN a.assignment asn " +
                "LEFT JOIN asn.employee e " +
                "LEFT JOIN asn.contract c " +
                "WHERE a.deleted = true " +
                "AND (:contractId IS NULL OR asn.contract.id = :contractId) " +
                "AND (:employeeId IS NULL OR asn.employee.id = :employeeId) " +
                "AND (:month IS NULL OR FUNCTION('MONTH', a.date) = :month) " +
                "AND (:year IS NULL OR FUNCTION('YEAR', a.date) = :year) " +
                "ORDER BY a.date DESC")
        org.springframework.data.domain.Page<Attendance> findDeletedByFilters(
                @Param("contractId") Long contractId,
                @Param("employeeId") Long employeeId,
                @Param("month") Integer month,
                @Param("year") Integer year,
                org.springframework.data.domain.Pageable pageable
        );

}
