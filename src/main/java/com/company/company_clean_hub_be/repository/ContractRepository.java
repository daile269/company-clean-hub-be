package com.company.company_clean_hub_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
}
