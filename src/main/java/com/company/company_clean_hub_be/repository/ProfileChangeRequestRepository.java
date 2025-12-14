package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.ProfileChangeRequest;
import com.company.company_clean_hub_be.entity.ProfileChangeRequest.RequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProfileChangeRequestRepository extends JpaRepository<ProfileChangeRequest, Long> {
    
    List<ProfileChangeRequest> findByEmployeeIdOrderByCreatedAtDesc(Long employeeId);
    
    List<ProfileChangeRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status);
    
    Page<ProfileChangeRequest> findByStatusOrderByCreatedAtDesc(RequestStatus status, Pageable pageable);
    
    @Query("SELECT p FROM ProfileChangeRequest p WHERE " +
           "(:employeeId IS NULL OR p.employee.id = :employeeId) AND " +
           "(:status IS NULL OR p.status = :status) " +
           "ORDER BY p.createdAt DESC")
    Page<ProfileChangeRequest> findByFilters(
        @Param("employeeId") Long employeeId,
        @Param("status") RequestStatus status,
        Pageable pageable
    );
    
    long countByStatusAndEmployeeId(RequestStatus status, Long employeeId);
}
