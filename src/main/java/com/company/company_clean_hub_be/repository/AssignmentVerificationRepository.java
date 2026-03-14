package com.company.company_clean_hub_be.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.company_clean_hub_be.entity.AssignmentVerification;
import com.company.company_clean_hub_be.entity.VerificationStatus;

public interface AssignmentVerificationRepository extends JpaRepository<AssignmentVerification, Long> {

    Optional<AssignmentVerification> findByAssignmentId(Long assignmentId);

    @Query("SELECT av FROM AssignmentVerification av WHERE av.assignment.employee.id = :employeeId")
    List<AssignmentVerification> findByEmployeeId(@Param("employeeId") Long employeeId);

    @Query("SELECT av FROM AssignmentVerification av WHERE av.status = :status")
    List<AssignmentVerification> findByStatus(@Param("status") VerificationStatus status);

    @Query("SELECT av FROM AssignmentVerification av WHERE av.assignment.employee.id = :employeeId AND av.status IN :statuses")
    List<AssignmentVerification> findByEmployeeIdAndStatusIn(@Param("employeeId") Long employeeId, @Param("statuses") List<VerificationStatus> statuses);

    @Query("SELECT COUNT(av) FROM AssignmentVerification av WHERE av.assignment.employee.id = :employeeId AND av.status IN ('APPROVED', 'AUTO_APPROVED')")
    Long countCompletedVerificationsByEmployee(@Param("employeeId") Long employeeId);

    @Query("SELECT av FROM AssignmentVerification av WHERE av.status = 'PENDING' OR av.status = 'IN_PROGRESS'")
    List<AssignmentVerification> findPendingVerifications();

    @Query("SELECT av FROM AssignmentVerification av WHERE av.assignment.contract.id = :contractId")
    List<AssignmentVerification> findByContractId(@Param("contractId") Long contractId);
    
    // Tìm verification cần auto-approve (đã đủ 5 lần và đang IN_PROGRESS)
    @Query("SELECT av FROM AssignmentVerification av " +
           "WHERE av.status = 'IN_PROGRESS' " +
           "AND av.currentAttempts >= av.maxAttempts")
    List<AssignmentVerification> findVerificationsForAutoApproval();
}