package com.company.company_clean_hub_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.company.company_clean_hub_be.entity.Assignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, Long> {
    
    @Query("SELECT a FROM Assignment a " +
           "LEFT JOIN a.employee e " +
           "LEFT JOIN a.contract cont " +
           "LEFT JOIN cont.customer c " +
           "WHERE (:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "CAST(a.id AS string) LIKE CONCAT('%', :keyword, '%'))")
    Page<Assignment> findByFilters(
            @Param("keyword") String keyword,
            Pageable pageable
    );
    
    @Query("SELECT a FROM Assignment a WHERE a.employee.id = :employeeId " +
           "AND a.startDate <= :endDate " +
           "ORDER BY a.startDate DESC")
    List<Assignment> findActiveAssignmentsByEmployee(
            @Param("employeeId") Long employeeId,
            @Param("endDate") LocalDate endDate
    );
    
    @Query("SELECT a FROM Assignment a " +
           "WHERE a.contract.customer.id = :customerId " +
           "AND a.status = 'IN_PROGRESS' " +
           "ORDER BY a.startDate DESC")
    List<Assignment> findActiveAssignmentsByCustomer(@Param("customerId") Long customerId);
    
    @Query("SELECT a FROM Assignment a " +
           "WHERE a.contract.customer.id = :customerId " +
           "ORDER BY a.startDate ASC")
    List<Assignment> findAllAssignmentsByCustomer(@Param("customerId") Long customerId);
    
    @Query("SELECT a FROM Assignment a " +
           "WHERE a.employee.id = :employeeId " +
           "AND a.contract.id = :contractId " +
           "AND a.status = 'IN_PROGRESS'")
    List<Assignment> findActiveAssignmentByEmployeeAndContract(
            @Param("employeeId") Long employeeId,
            @Param("contractId") Long contractId
    );
    
    @Query("SELECT a FROM Assignment a " +
           "WHERE a.employee.id = :employeeId " +
           "AND a.contract.id = :contractId " +
           "AND a.status = 'IN_PROGRESS' " +
           "AND a.id != :assignmentId")
    List<Assignment> findActiveAssignmentByEmployeeAndContractAndIdNot(
            @Param("employeeId") Long employeeId,
            @Param("contractId") Long contractId,
            @Param("assignmentId") Long assignmentId
    );

       @Query("SELECT DISTINCT a.contract.customer FROM Assignment a " +
                 "WHERE a.employee.id = :employeeId " +
                 "AND a.status = 'IN_PROGRESS'")
       List<com.company.company_clean_hub_be.entity.Customer> findActiveCustomersByEmployee(@Param("employeeId") Long employeeId);
    @Query("SELECT DISTINCT a FROM Assignment a " +
            "JOIN a.attendances att " +
            "WHERE a.employee.id = :employeeId " +
            "AND FUNCTION('MONTH', att.date) = :month " +
            "AND FUNCTION('YEAR', att.date) = :year")
    List<Assignment> findDistinctAssignmentsByAttendanceMonthAndEmployee(
            @Param("month") Integer month,
            @Param("year") Integer year,
            @Param("employeeId") Long employeeId);

       @Query("SELECT a FROM Assignment a " +
              "WHERE a.assignmentType = 'TEMPORARY' " +
              "AND a.status = 'IN_PROGRESS' " +
              "AND a.startDate <= :date")
       List<Assignment> findExpiredTemporaryAssignments(@Param("date") LocalDate date);
       
       @Query("SELECT a FROM Assignment a " +
              "WHERE (a.assignmentType = 'FIXED_BY_CONTRACT' OR a.assignmentType = 'FIXED_BY_DAY') " +
              "AND a.status = 'IN_PROGRESS' " +
              "AND a.startDate <= :endOfLastMonth")
       List<Assignment> findExpiredFixedAssignments(@Param("endOfLastMonth") LocalDate endOfLastMonth);
}
