package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.AssignmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.company_clean_hub_be.entity.Employee;

import java.util.List;

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
           "LOWER(e.phone) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Employee> findByFilters(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    boolean existsByUsername(String username);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, Long id);

    @Query("SELECT e FROM Employee e " +
            "WHERE e.id NOT IN (" +
            "SELECT a.employee.id FROM Assignment a " +
            "WHERE a.contract.customer.id = :customerId " +
            "AND a.status = 'IN_PROGRESS')")
    Page<Employee> findEmployeesNotAssignedToCustomer(
            @Param("customerId") Long customerId,
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


}
