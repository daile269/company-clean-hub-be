package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.AssignmentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.company.company_clean_hub_be.entity.Assignment;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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

    @Query(value = "SELECT * FROM assignments " +
            "WHERE employee_id = :employeeId " +
            "AND MONTH(start_date) = :month " +
            "AND YEAR(start_date) = :year",
            nativeQuery = true)
    List<Assignment> findAssignmentsByEmployeeAndMonthAndYear(
            @Param("employeeId") Long employeeId,
            @Param("month") Integer month,
            @Param("year") Integer year
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
           "WHERE a.contract.customer.id = :customerId " +
           "AND (:contractType IS NULL OR a.contract.contractType = :contractType) " +
           "AND (:status IS NULL OR a.status = :status) " +
           "AND (:month IS NULL OR MONTH(a.startDate) = :month) " +
           "AND (:year IS NULL OR YEAR(a.startDate) = :year) " +
           "ORDER BY a.startDate DESC")
    Page<Assignment> findAllAssignmentsByCustomerWithFilters(
            @Param("customerId") Long customerId,
            @Param("contractType") com.company.company_clean_hub_be.entity.ContractType contractType,
            @Param("status") AssignmentStatus status,
            @Param("month") Integer month,
            @Param("year") Integer year,
            Pageable pageable
    );
    
    @Query("SELECT a FROM Assignment a " +
           "WHERE a.employee.id = :employeeId " +
           "AND a.contract.id = :contractId " +
           "AND a.status = 'IN_PROGRESS'")
    List<Assignment> findActiveAssignmentByEmployeeAndContract(
            @Param("employeeId") Long employeeId,
            @Param("contractId") Long contractId
    );

    @Query("SELECT a FROM Assignment a " +
            "LEFT JOIN a.contract c " +
            "LEFT JOIN c.customer cus " +
            "WHERE a.employee.id = :employeeId " +
            "AND (:customerId IS NULL OR cus.id = :customerId) " +
            "AND (:month IS NULL OR MONTH(a.startDate) = :month) " +
            "AND (:year IS NULL OR YEAR(a.startDate) = :year) " +
            "ORDER BY a.startDate DESC")
    Page<Assignment> findAssignmentsByEmployeeWithFilters(
            @Param("employeeId") Long employeeId,
            @Param("customerId") Long customerId,
            @Param("month") Integer month,
            @Param("year") Integer year,
            Pageable pageable
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
            "AND FUNCTION('YEAR', att.date) = :year " +
            "AND a.status <> com.company.company_clean_hub_be.entity.AssignmentStatus.CANCELLED")
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
              "WHERE a.employee.id = :employeeId " +
              "AND a.contract.id = :contractId " +
              "AND YEAR(a.startDate) = :year " +
              "AND MONTH(a.startDate) = :month")
       Optional<Assignment> findByEmployeeAndContractAndMonth(
               @Param("employeeId") Long employeeId,
               @Param("contractId") Long contractId,
               @Param("year") int year,
               @Param("month") int month
       );
       
       @Query("SELECT a FROM Assignment a " +
              "WHERE (a.assignmentType = 'FIXED_BY_CONTRACT' OR a.assignmentType = 'FIXED_BY_DAY') " +
              "AND a.status = 'IN_PROGRESS' " +
              "AND a.startDate <= :endOfLastMonth")
       List<Assignment> findExpiredFixedAssignments(@Param("endOfLastMonth") LocalDate endOfLastMonth);

        List<Assignment> findByEmployeeId(Long employeeId);

        @Query("SELECT a FROM Assignment a WHERE a.contract.id = :contractId")
        List<Assignment> findByContractId(@Param("contractId") Long contractId);

        @Query("SELECT a FROM Assignment a WHERE a.assignmentType = :type AND a.status = :status")
        List<Assignment> findByAssignmentTypeAndStatus(@Param("type") com.company.company_clean_hub_be.entity.AssignmentType type,
                                                      @Param("status") com.company.company_clean_hub_be.entity.AssignmentStatus status);

            @Query("SELECT COUNT(DISTINCT a.employee.id) FROM Assignment a " +
                   "WHERE a.contract.id = :contractId " +
                   "AND a.status = 'IN_PROGRESS' " +
                     "AND a.startDate <= :endDate")
            Long countDistinctActiveEmployeesByContractBefore(
                    @Param("contractId") Long contractId,
                    @Param("endDate") java.time.LocalDate endDate
            );

             @Query("SELECT COUNT(DISTINCT a.employee.id) FROM Assignment a " +
                     "WHERE a.contract.id = :contractId " +
                     "AND a.status = 'IN_PROGRESS' " +
                     "AND a.startDate <= :endDate " +
                     "AND (:excludedType IS NULL OR a.assignmentType <> :excludedType)")
             Long countDistinctActiveEmployeesByContractBeforeExcludingType(
                      @Param("contractId") Long contractId,
                      @Param("endDate") java.time.LocalDate endDate,
                      @Param("excludedType") com.company.company_clean_hub_be.entity.AssignmentType excludedType
             );

            @Query("SELECT a FROM Assignment a " +
                   "WHERE a.contract.id = :contractId " +
                   "AND (:status IS NULL OR a.status = :status) " +
                   "AND (:month IS NULL OR MONTH(a.startDate) = :month) " +
                   "AND (:year IS NULL OR YEAR(a.startDate) = :year) " +
                   "ORDER BY a.startDate DESC")
            Page<Assignment> findByContractIdWithFilters(
                    @Param("contractId") Long contractId,
                    @Param("status") com.company.company_clean_hub_be.entity.AssignmentStatus status,
                    @Param("month") Integer month,
                    @Param("year") Integer year,
                    Pageable pageable
            );

            @Query("SELECT a FROM Assignment a WHERE a.status = :status AND a.startDate = :startDate")
            List<Assignment> findByStatusAndStartDate(
                    @Param("status") AssignmentStatus status,
                    @Param("startDate") LocalDate startDate
            );

            @Query("SELECT a FROM Assignment a WHERE a.endDate = :endDate AND a.status = :status")
            List<Assignment> findAllByEndDateAndStatus(
                    @Param("endDate") LocalDate endDate,
                    @Param("status") AssignmentStatus status
            );
}
