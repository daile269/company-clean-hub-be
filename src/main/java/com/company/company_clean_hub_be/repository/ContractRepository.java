package com.company.company_clean_hub_be.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.company_clean_hub_be.dto.response.CustomerContractServiceFlatDto;
import com.company.company_clean_hub_be.entity.Contract;

public interface ContractRepository extends JpaRepository<Contract, Long> {
    
    @Query("SELECT c FROM Contract c LEFT JOIN c.customer cu WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "CAST(c.id AS string) LIKE CONCAT('%', :keyword, '%') OR " +
           "LOWER(cu.name) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(cu.customerCode) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<Contract> findByFilters(
            @Param("keyword") String keyword,
            Pageable pageable
    );
    
    List<Contract> findByCustomerId(Long customerId);

    @Query("""
            SELECT new com.company.company_clean_hub_be.dto.response.CustomerContractServiceFlatDto(
                c.customer.id,
                c.customer.name,
                c.customer.address,
                c.customer.taxCode,
                c.customer.email,
                c.id,
                CAST(c.startDate AS string),
                CAST(c.endDate AS string),
                CAST(c.contractType AS string),
                c.paymentStatus,
                c.description,
                CAST(SIZE(c.workingDaysPerWeek) AS int),
                s.id,
                s.title,
                s.price,
                s.vat
            )
            FROM Contract c
            LEFT JOIN c.services s
            ORDER BY c.customer.id ASC, c.id ASC, s.id ASC
            """)
    List<CustomerContractServiceFlatDto> findAllCustomerContractServicesFlat();
}
