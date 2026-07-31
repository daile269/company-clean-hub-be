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

    @Query("SELECT av FROM AssignmentVerification av " +
           "JOIN FETCH av.assignment a " +
           "JOIN FETCH a.employee " +
           "WHERE av.id = :id")
    Optional<AssignmentVerification> findByIdWithEmployee(@Param("id") Long id);

    @Query("SELECT av FROM AssignmentVerification av WHERE av.assignment.employee.id = :employeeId")
    List<AssignmentVerification> findByEmployeeId(@Param("employeeId") Long employeeId);

    @Query("SELECT av FROM AssignmentVerification av WHERE av.status = :status")
    List<AssignmentVerification> findByStatus(@Param("status") VerificationStatus status);

    @Query("SELECT av FROM AssignmentVerification av WHERE av.assignment.employee.id = :employeeId AND av.status IN :statuses")
    List<AssignmentVerification> findByEmployeeIdAndStatusIn(@Param("employeeId") Long employeeId, @Param("statuses") List<VerificationStatus> statuses);

    @Query("SELECT COUNT(av) FROM AssignmentVerification av WHERE av.assignment.employee.id = :employeeId AND av.status IN ('APPROVED', 'AUTO_APPROVED', 'BYPASS_APPROVED')")
    Long countCompletedVerificationsByEmployee(@Param("employeeId") Long employeeId);

    @Query("SELECT av FROM AssignmentVerification av WHERE (av.status = 'PENDING' OR av.status = 'IN_PROGRESS') AND av.reason = 'NEW_EMPLOYEE'")
    List<AssignmentVerification> findPendingVerifications();

    @Query("SELECT av FROM AssignmentVerification av WHERE av.assignment.contract.id = :contractId")
    List<AssignmentVerification> findByContractId(@Param("contractId") Long contractId);
    
    // Tìm verification cần auto-approve (đã có attempt, đang pending/in-progress)
    @Query("SELECT av FROM AssignmentVerification av " +
           "WHERE av.status IN ('PENDING', 'IN_PROGRESS') " +
           "AND av.currentAttempts > 0")
    List<AssignmentVerification> findVerificationsForAutoApproval();

    // Lấy tất cả verifications của nhân viên làm việc theo hợp đồng của 1 customer cụ thể
    @Query("SELECT av FROM AssignmentVerification av " +
           "JOIN FETCH av.assignment a " +
           "JOIN FETCH a.employee " +
           "WHERE a.contract.customer.id = :customerId " +
           "ORDER BY av.createdAt DESC")
    List<AssignmentVerification> findByCustomerId(@Param("customerId") Long customerId);
}