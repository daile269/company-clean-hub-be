package com.company.company_clean_hub_be.repository;

import java.util.List;

import com.company.company_clean_hub_be.entity.EmploymentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.company_clean_hub_be.entity.Employee;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    boolean existsByEmployeeCode(String employeeCode);
    boolean existsByCccd(String cccd);
    boolean existsByBankAccount(String bankAccount);
    boolean existsByEmployeeCodeAndIdNot(String employeeCode, Long id);
    boolean existsByCccdAndIdNot(String cccd, Long id);
    boolean existsByBankAccountAndIdNot(String bankAccount, Long id);
    
    @Query("SELECT e FROM Employee e WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(e.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(e.employeeCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(e.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:employmentType IS NULL OR e.employmentType = :employmentType)")
    Page<Employee> findByFilters(
            @Param("keyword") String keyword,
            @Param("employmentType") com.company.company_clean_hub_be.entity.EmploymentType employmentType,
            Pageable pageable
    );

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    @Query("SELECT e FROM Employee e " +
            "WHERE (:employmentType IS NULL OR e.employmentType = :employmentType) " +
            "AND e.id NOT IN (" +
            "SELECT a.employee.id FROM Assignment a " +
            "WHERE a.contract.customer.id = :customerId " +
            "AND a.status = 'IN_PROGRESS')")
    Page<Employee> findEmployeesNotAssignedToCustomer(
            @Param("customerId") Long customerId,
            @Param("employmentType") com.company.company_clean_hub_be.entity.EmploymentType employmentType,
            Pageable pageable
    );
    
    @Query("SELECT e FROM Employee e " +
            "WHERE (:employmentType IS NULL OR e.employmentType = :employmentType) " +
            "AND e.id NOT IN (" +
            "SELECT a.employee.id FROM Assignment a " +
            "WHERE a.contract.customer.id = :customerId " +
            "AND (:month IS NULL OR MONTH(a.startDate) = :month) " +
            "AND (:year IS NULL OR YEAR(a.startDate) = :year))")
    Page<Employee> findEmployeesNotAssignedToCustomerByMonth(
            @Param("customerId") Long customerId,
            @Param("employmentType") com.company.company_clean_hub_be.entity.EmploymentType employmentType,
            @Param("month") Integer month,
            @Param("year") Integer year,
            Pageable pageable
    );
    @Query("""
        SELECT DISTINCT e 
        FROM Employee e
        JOIN Assignment a ON e.id = a.employee.id
        WHERE FUNCTION('MONTH', a.startDate) = :month
          AND FUNCTION('YEAR', a.startDate) = :year
    """)
    List<Employee> findDistinctEmployeesByAssignmentMonthYear(
            @Param("month") int month,
            @Param("year") int year
    );

    List<Employee> findByEmploymentType(EmploymentType employmentType);

}
