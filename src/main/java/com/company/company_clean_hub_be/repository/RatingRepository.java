package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface RatingRepository extends JpaRepository<Rating, Long>, JpaSpecificationExecutor<Rating> {
    void deleteByAssignmentId(Long assignmentId);
    void deleteByEmployeeId(Long employeeId);
    void deleteByContractId(Long contractId);
    List<Rating> findByContractId(Long contractId);
    List<Rating> findByEmployeeId(Long employeeId);
    List<Rating> findByReviewerId(Long reviewerId);
    List<Rating> findByContractCustomerId(Long customerId);
    List<Rating> findByContractCustomerIdAndEmployeeIsNotNull(Long customerId);
}

