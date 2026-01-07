package com.company.company_clean_hub_be.repository;

import com.company.company_clean_hub_be.entity.Rating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RatingRepository extends JpaRepository<Rating, Long>, JpaSpecificationExecutor<Rating> {
    void deleteByAssignmentId(Long assignmentId);
    void deleteByEmployeeId(Long employeeId);
    void deleteByContractId(Long contractId);
    List<Rating> findByContractId(Long contractId);
    // Lấy các rating cho contract: reviewer IS NULL và employee IS NOT NULL (JPQL)
    @Query("SELECT r FROM Rating r WHERE r.contract.id = :contractId AND r.reviewer IS NULL AND r.employee IS NOT NULL")
    List<Rating> findByContractIdAndReviewerIsNullAndEmployeeIsNotNull(@Param("contractId") Long contractId);
    List<Rating> findByEmployeeId(Long employeeId);
    List<Rating> findByReviewerId(Long reviewerId);
    List<Rating> findByContractCustomerId(Long customerId);
    // List<Rating> findByContractCustomerIdAndEmployeeIsNotNullAndReviewerIsNull(Long customerId);

    // JPQL: lấy các rating chưa được review (reviewer IS NULL) nhưng có employee (employee IS NOT NULL)
    @Query("SELECT r FROM Rating r WHERE r.contract.customer.id = :customerId AND r.reviewer IS NULL AND r.employee IS NOT NULL")
    List<Rating> findByContractCustomerIdAndReviewerIsNullAndEmployeeIsNotNull(@Param("customerId") Long customerId);
}

