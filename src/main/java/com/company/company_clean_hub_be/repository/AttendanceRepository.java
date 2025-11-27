package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.Attendance;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface AttendanceRepository extends JpaRepository<Attendance, Long> {
    
    @Query("SELECT a FROM Attendance a WHERE a.employee.id = :employeeId " +
           "AND a.date BETWEEN :startDate AND :endDate")
    List<Attendance> findByEmployeeAndDateBetween(
            @Param("employeeId") Long employeeId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );
    
    @Query("SELECT a FROM Attendance a WHERE a.employee.id = :employeeId AND a.date = :date")
    Optional<Attendance> findByEmployeeAndDate(
            @Param("employeeId") Long employeeId,
            @Param("date") LocalDate date
    );
    
    @Query("SELECT a FROM Attendance a " +
           "LEFT JOIN a.employee e " +
           "LEFT JOIN a.assignment asn " +
           "LEFT JOIN asn.customer c " +
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
           "WHERE a.employee.id = :employeeId " +
           "AND (:month IS NULL OR MONTH(a.date) = :month) " +
           "AND (:year IS NULL OR YEAR(a.date) = :year) " +
           "ORDER BY a.date DESC")
    Page<Attendance> findByEmployeeAndFilters(
            @Param("employeeId") Long employeeId,
            @Param("month") Integer month,
            @Param("year") Integer year,
            Pageable pageable
    );
}
