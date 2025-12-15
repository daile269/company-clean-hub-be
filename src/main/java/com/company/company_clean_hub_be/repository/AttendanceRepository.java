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
            "AND a.date BETWEEN :startDate AND :endDate")
    List<Attendance> findByEmployeeAndDateBetween(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
    
    @Query("SELECT a FROM Attendance a WHERE a.assignment.employee.id = :employeeId AND a.date = :date")
    Optional<Attendance> findByEmployeeAndDate(
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date
    );
    
    @Query("SELECT a FROM Attendance a WHERE a.assignment.employee.id = :employeeId AND a.date = :date")
    List<Attendance> findAllByEmployeeAndDate(
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date
    );
    
    @Query("SELECT a FROM Attendance a WHERE a.assignment.id = :assignmentId AND a.assignment.employee.id = :employeeId AND a.date = :date")
    Optional<Attendance> findByAssignmentAndEmployeeAndDate(
            @Param("assignmentId") Long assignmentId,
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date
    );
    
    @Query("SELECT a FROM Attendance a " +
            "LEFT JOIN a.assignment asn " +
            "LEFT JOIN asn.employee e " +
            "LEFT JOIN asn.contract.customer c " +
           "WHERE (:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:month IS NULL OR MONTH(a.date) = :month) " +
           "AND (:year IS NULL OR YEAR(a.date) = :year)")
    Page<Attendance> findByFilters(
            @Param("keyword") String keyword,
            @Param("month") Integer month,
            @Param("year") Integer year,
            Pageable pageable
    );

    @Query("SELECT a FROM Attendance a " +
            "WHERE a.assignment.employee.id = :employeeId " +
            "AND (:month IS NULL OR MONTH(a.date) = :month) " +
            "AND (:year IS NULL OR YEAR(a.date) = :year) " +
            "ORDER BY a.date DESC")
    Page<Attendance> findByEmployeeAndFilters(
            @Param("employeeId") Long employeeId,
            @Param("month") Integer month,
            @Param("year") Integer year,
            Pageable pageable
    );
    @Query("SELECT SUM(a.bonus) FROM Attendance a WHERE a.assignment.id = :assignmentId")
    BigDecimal sumBonusByAssignment(@Param("assignmentId") Long assignmentId);
    @Query("SELECT SUM(a.penalty) FROM Attendance a WHERE a.assignment.id = :assignmentId")
    BigDecimal sumPenaltyByAssignment(@Param("assignmentId") Long assignmentId);
    @Query("SELECT SUM(a.supportCost) FROM Attendance a WHERE a.assignment.id = :assignmentId")
    BigDecimal sumSupportCostByAssignment(@Param("assignmentId") Long assignmentId);

    @Query("SELECT a FROM Attendance a " +
            "WHERE a.assignment.employee.id = :employeeId " +
            "AND FUNCTION('MONTH', a.date) = :month " +
            "AND FUNCTION('YEAR', a.date) = :year")
    List<Attendance> findAttendancesByMonthYearAndEmployee(
            @Param("month") Integer month,
            @Param("year") Integer year,
            @Param("employeeId") Long employeeId);

    @Query("SELECT a FROM Attendance a WHERE a.payroll.id = :payrollId")
    List<Attendance> findByPayrollId(@Param("payrollId") Long payrollId);

    @Query("SELECT a FROM Attendance a " +
            "WHERE a.assignment.id = :assignmentId " +
            "AND (:month IS NULL OR MONTH(a.date) = :month) " +
            "AND (:year IS NULL OR YEAR(a.date) = :year) " +
            "ORDER BY a.date DESC")
    Page<Attendance> findByAssignmentAndFilters(
            @Param("assignmentId") Long assignmentId,
            @Param("month") Integer month,
            @Param("year") Integer year,
            Pageable pageable
    );
    
    @Query("SELECT a FROM Attendance a WHERE a.assignment.id = :assignmentId AND a.date BETWEEN :startDate AND :endDate")
    List<Attendance> findByAssignmentAndDateBetween(
            @Param("assignmentId") Long assignmentId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );


}
