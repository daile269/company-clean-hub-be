package com.company.company_clean_hub_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.company_clean_hub_be.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    @Query("SELECT c FROM Customer c WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.taxCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:hasContractInMonth IS NULL OR " +
           "(:hasContractInMonth = true AND c.id IN (" +
           "  SELECT DISTINCT ct.customer.id FROM Contract ct WHERE ct.startDate <= :monthEnd AND (ct.endDate IS NULL OR ct.endDate >= :monthStart)" +
           ")) OR " +
           "(:hasContractInMonth = false AND c.id NOT IN (" +
           "  SELECT DISTINCT ct.customer.id FROM Contract ct WHERE ct.startDate <= :monthEnd AND (ct.endDate IS NULL OR ct.endDate >= :monthStart)" +
           ")))")
    Page<Customer> findByFilters(
            @Param("keyword") String keyword,
            @Param("hasContractInMonth") Boolean hasContractInMonth,
            @Param("monthStart") java.time.LocalDate monthStart,
            @Param("monthEnd") java.time.LocalDate monthEnd,
            Pageable pageable
    );

    @Query("SELECT c FROM Customer c WHERE c.id IN :ids AND " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.customerCode) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.phone) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.email) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(c.taxCode) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:hasContractInMonth IS NULL OR " +
           "(:hasContractInMonth = true AND c.id IN (" +
           "  SELECT DISTINCT ct.customer.id FROM Contract ct WHERE ct.startDate <= :monthEnd AND (ct.endDate IS NULL OR ct.endDate >= :monthStart)" +
           ")) OR " +
           "(:hasContractInMonth = false AND c.id NOT IN (" +
           "  SELECT DISTINCT ct.customer.id FROM Contract ct WHERE ct.startDate <= :monthEnd AND (ct.endDate IS NULL OR ct.endDate >= :monthStart)" +
           ")))")
    Page<Customer> findByFiltersAndIds(
            @Param("keyword") String keyword,
            @Param("ids") java.util.List<Long> ids,
            @Param("hasContractInMonth") Boolean hasContractInMonth,
            @Param("monthStart") java.time.LocalDate monthStart,
            @Param("monthEnd") java.time.LocalDate monthEnd,
            Pageable pageable
    );

        java.util.Optional<Customer> findByCustomerCode(String customerCode);

        // Phương thức để lấy mã khách hàng lớn nhất
        @Query("SELECT c.customerCode FROM Customer c WHERE c.customerCode LIKE 'KH%' ORDER BY c.customerCode DESC")
        java.util.List<String> findTopByCustomerCodeStartingWithKH(Pageable pageable);
}
