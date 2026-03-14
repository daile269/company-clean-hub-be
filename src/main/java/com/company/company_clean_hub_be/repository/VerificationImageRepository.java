package com.company.company_clean_hub_be.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.company.company_clean_hub_be.entity.VerificationImage;

public interface VerificationImageRepository extends JpaRepository<VerificationImage, Long> {

    List<VerificationImage> findByAssignmentVerificationId(Long assignmentVerificationId);

    List<VerificationImage> findByEmployeeId(Long employeeId);

    List<VerificationImage> findByAttendanceId(Long attendanceId);

    Optional<VerificationImage> findByCloudinaryPublicId(String cloudinaryPublicId);

    @Query("SELECT vi FROM VerificationImage vi WHERE vi.assignmentVerification.id = :verificationId ORDER BY vi.capturedAt DESC")
    List<VerificationImage> findByAssignmentVerificationIdOrderByCapturedAtDesc(@Param("verificationId") Long verificationId);

    @Query("SELECT vi FROM VerificationImage vi WHERE vi.employee.id = :employeeId AND vi.capturedAt BETWEEN :startDate AND :endDate")
    List<VerificationImage> findByEmployeeIdAndCapturedAtBetween(
        @Param("employeeId") Long employeeId, 
        @Param("startDate") LocalDateTime startDate, 
        @Param("endDate") LocalDateTime endDate
    );

    @Query("SELECT COUNT(vi) FROM VerificationImage vi WHERE vi.assignmentVerification.id = :verificationId")
    Long countByAssignmentVerificationId(@Param("verificationId") Long verificationId);

    @Query("SELECT vi FROM VerificationImage vi WHERE vi.assignmentVerification.assignment.employee.id = :employeeId ORDER BY vi.capturedAt DESC")
    List<VerificationImage> findByEmployeeIdOrderByCapturedAtDesc(@Param("employeeId") Long employeeId);
    
    // Kiểm tra đã chụp ảnh trong ngày hôm nay chưa
    @Query("SELECT CASE WHEN COUNT(vi) > 0 THEN true ELSE false END " +
           "FROM VerificationImage vi " +
           "WHERE vi.assignmentVerification.id = :verificationId " +
           "AND FUNCTION('DATE', vi.capturedAt) = FUNCTION('DATE', :dateTime)")
    boolean existsByVerificationIdAndCapturedDate(
        @Param("verificationId") Long verificationId, 
        @Param("dateTime") LocalDateTime dateTime);
    
    // Lấy ngày chụp ảnh cuối cùng của assignment
    @Query("SELECT vi.capturedAt " +
           "FROM VerificationImage vi " +
           "WHERE vi.assignmentVerification.assignment.id = :assignmentId " +
           "ORDER BY vi.capturedAt DESC")
    List<LocalDateTime> findCaptureDatesByAssignmentId(@Param("assignmentId") Long assignmentId);
}