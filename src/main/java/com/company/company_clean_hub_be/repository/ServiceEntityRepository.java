package com.company.company_clean_hub_be.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.company_clean_hub_be.entity.ServiceEntity;

public interface ServiceEntityRepository extends JpaRepository<ServiceEntity, Long> {
    
    @Query("SELECT s FROM ServiceEntity s WHERE " +
           "(:keyword IS NULL OR :keyword = '' OR " +
           "CAST(s.id AS string) LIKE CONCAT('%', :keyword, '%') OR " +
           "LOWER(s.title) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<ServiceEntity> findByFilters(
            @Param("keyword") String keyword,
            Pageable pageable
    );
}
