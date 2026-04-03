package com.company.company_clean_hub_be.service.impl;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.dto.request.VerificationApprovalRequest;
import com.company.company_clean_hub_be.dto.request.VerificationCaptureRequest;
import com.company.company_clean_hub_be.dto.response.AssignmentVerificationResponse;
import com.company.company_clean_hub_be.dto.response.VerificationImageResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.AssignmentVerification;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.entity.VerificationImage;
import com.company.company_clean_hub_be.entity.VerificationReason;
import com.company.company_clean_hub_be.entity.VerificationStatus;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.exception.ResourceNotFoundException;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AssignmentVerificationRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.ContractRepository;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.repository.VerificationImageRepository;
import com.company.company_clean_hub_be.service.FileStorageService;
import com.company.company_clean_hub_be.service.VerificationService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class VerificationServiceImpl implements VerificationService {

    private final AssignmentVerificationRepository verificationRepository;
    private final VerificationImageRepository imageRepository;
    private final AssignmentRepository assignmentRepository;
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final FileStorageService fileStorageService;

    @Override
    @Transactional
    public AssignmentVerification createVerificationRequirement(Assignment assignment, String reasonStr) {
        log.info("[DEBUG] ===== createVerificationRequirement called =====");
        log.info("[DEBUG] Assignment ID: {}, Reason: {}", assignment.getId(), reasonStr);
        log.info("Creating verification requirement for assignment: {}, reason: {}", assignment.getId(), reasonStr);

        // Check if verification already exists
        Optional<AssignmentVerification> existing = verificationRepository.findByAssignmentId(assignment.getId());
        if (existing.isPresent()) {
            log.warn("[DEBUG] Verification requirement already exists for assignment: {}", assignment.getId());
            log.warn("Verification requirement already exists for assignment: {}", assignment.getId());
            return existing.get();
        }

        log.info("[DEBUG] Creating new verification requirement");
        VerificationReason reason = VerificationReason.valueOf(reasonStr);
        log.info("[DEBUG] Reason enum: {}", reason);

        AssignmentVerification verification = AssignmentVerification.builder()
                .assignment(assignment)
                .reason(reason)
                .status(VerificationStatus.PENDING)
                .maxAttempts(5)
                .currentAttempts(0)
                .build();

        AssignmentVerification saved = verificationRepository.save(verification);
        log.info("[DEBUG] ===== Verification requirement created: {} =====", saved.getId());
        log.info("Created verification requirement: {}", saved.getId());
        return saved;
    }

    @Override
    public Optional<AssignmentVerificationResponse> getVerificationByAssignmentId(Long assignmentId) {
        log.info("[VerificationService] getVerificationByAssignmentId called with assignmentId={}", assignmentId);

        // 1. Check if assignment exists
        var assignmentOpt = assignmentRepository.findById(assignmentId);
        if (assignmentOpt.isEmpty()) {
            log.warn("[VerificationService] Assignment with id={} does NOT exist in DB!", assignmentId);
            return Optional.empty();
        }
        var assignment = assignmentOpt.get();
        log.info("[VerificationService] Assignment found: id={}, employeeId={}, status={}, contractId={}",
                assignment.getId(),
                assignment.getEmployee() != null ? assignment.getEmployee().getId() : "NULL",
                assignment.getStatus(),
                assignment.getContract() != null ? assignment.getContract().getId() : "NULL");

        // 2. Check if verification exists for this assignment
        Optional<AssignmentVerification> verificationOpt = verificationRepository.findByAssignmentId(assignmentId);
        if (verificationOpt.isEmpty()) {
            log.warn(
                    "[VerificationService] No verification record found in assignment_verifications for assignmentId={}.",
                    assignmentId);

            // 3. Debug: check if verification is needed
            boolean needsVerification = requiresVerification(assignment);
            log.info("[VerificationService] Does assignment {} require verification? -> {}", assignmentId,
                    needsVerification);

            if (needsVerification) {
                log.info("[VerificationService] Verification IS required but was never created. " +
                        "This may happen if the assignment was created via the old flow (autoGenerateAttendances) " +
                        "instead of autoGenerateAttendancesWithVerification.");
            }

            // 4. Debug: list all verifications for this employee
            if (assignment.getEmployee() != null) {
                var allEmployeeVerifications = verificationRepository
                        .findByEmployeeId(assignment.getEmployee().getId());
                log.info("[VerificationService] Total verifications for employeeId={}: count={}",
                        assignment.getEmployee().getId(), allEmployeeVerifications.size());
                for (var v : allEmployeeVerifications) {
                    log.info("[VerificationService]   - verificationId={}, assignmentId={}, status={}, reason={}",
                            v.getId(), v.getAssignment().getId(), v.getStatus(), v.getReason());
                }
            }

            return Optional.empty();
        }

        AssignmentVerification verification = verificationOpt.get();
        log.info("[VerificationService] Verification FOUND: id={}, status={}, reason={}, attempts={}/{}, canCapture={}",
                verification.getId(), verification.getStatus(), verification.getReason(),
                verification.getCurrentAttempts(), verification.getMaxAttempts(), verification.canCapture());

        return Optional.of(mapToVerificationResponse(verification));
    }

    @Override
    public List<AssignmentVerificationResponse> getPendingVerifications() {
        return verificationRepository.findPendingVerifications()
                .stream()
                .map(this::mapToVerificationResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<VerificationImageResponse> getImagesByAttendanceId(Long attendanceId) {
        return imageRepository.findByAttendanceId(attendanceId)
                .stream()
                .map(this::mapToImageResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public VerificationImageResponse captureVerificationImage(VerificationCaptureRequest request) {
        log.info("[DEBUG] ===== captureVerificationImage called =====");
        log.info("[DEBUG] Verification ID: {}", request.getVerificationId());
        log.info("Capturing verification image for verification: {}", request.getVerificationId());

        AssignmentVerification verification = verificationRepository.findById(request.getVerificationId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Verification not found: " + request.getVerificationId()));

        if (!verification.canCapture()) {
            log.warn("[DEBUG] Cannot capture image - verification not eligible");
            throw new AppException(ErrorCode.VERIFICATION_CAPTURE_NOT_ALLOWED);
        }

        try {
            // Upload image to Cloudinary
            String fileName = "verification_" + verification.getId() + "_" + System.currentTimeMillis();
            String publicId = fileStorageService.storeBase64(request.getImageData(), fileName,
                    "company-clean-hub/verification");
            String imageUrl = fileStorageService.getSecureUrl(publicId);

            // Create verification image record
            VerificationImage image = VerificationImage.builder()
                    .assignmentVerification(verification)
                    .employee(verification.getAssignment().getEmployee())
                    .attendance(request.getAttendanceId() != null
                            ? attendanceRepository.findById(request.getAttendanceId()).orElse(null)
                            : null)
                    .cloudinaryPublicId(publicId)
                    .cloudinaryUrl(imageUrl)
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .address(request.getAddress())
                    .capturedAt(LocalDateTime.now())
                    .faceConfidence(request.getFaceConfidence())
                    .imageQualityScore(request.getImageQualityScore())
                    .build();

            VerificationImage savedImage = imageRepository.save(image);

            // Update verification status and attempts
            verification.incrementAttempts();
            if (verification.getStatus() == VerificationStatus.PENDING) {
                verification.setStatus(VerificationStatus.IN_PROGRESS);
            }
            verificationRepository.save(verification);

            log.info("[DEBUG] Image saved: id={}, attempts: {}/{}", 
                    savedImage.getId(), verification.getCurrentAttempts(), verification.getMaxAttempts());
            log.info("Captured verification image: {}, attempts: {}/{}",
                    savedImage.getId(), verification.getCurrentAttempts(), verification.getMaxAttempts());

            // CRITICAL FIX: Trigger attendance generation for next day
            log.info("[DEBUG] Triggering attendance generation after image capture");
            triggerAttendanceGeneration(verification.getAssignment());
            log.info("[DEBUG] ===== Attendance generation triggered =====");

            return mapToImageResponse(savedImage);

        } catch (IOException e) {
            log.error("[DEBUG] Failed to upload verification image: {}", e.getMessage(), e);
            log.error("Failed to upload verification image", e);
            throw new AppException(ErrorCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    public List<VerificationImageResponse> getVerificationImages(Long verificationId) {
        return imageRepository.findByAssignmentVerificationIdOrderByCapturedAtDesc(verificationId)
                .stream()
                .map(this::mapToImageResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public AssignmentVerificationResponse approveVerification(VerificationApprovalRequest request,
            String approverUsername) {
        log.info("Approving verification: {} by user: {}", request.getVerificationId(), approverUsername);

        AssignmentVerification verification = verificationRepository.findById(request.getVerificationId())
                .orElseThrow(
                        () -> new ResourceNotFoundException("Verification not found: " + request.getVerificationId()));

        User approver = userRepository.findByUsername(approverUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + approverUsername));

        verification.setStatus(VerificationStatus.APPROVED);
        verification.setApprovedBy(approver);
        verification.setApprovedAt(LocalDateTime.now());

        AssignmentVerification saved = verificationRepository.save(verification);
        log.info("Approved verification: {}", saved.getId());

        // Business rule: once a manager approves verification, always disable image verification on the contract
        // (disableVerification flag is kept for backward compatibility but no longer gates this behavior)
        Contract contract = verification.getAssignment() != null ? verification.getAssignment().getContract() : null;
        if (contract != null) {
            log.info("[DEBUG] Disabling verification for contract: {}", contract.getId());
            contract.setRequiresImageVerification(false);
            contractRepository.save(contract);
        }

        // FIXED: Generate ALL remaining attendances for the month after approval
        log.info("[DEBUG] Generating all remaining attendances after approval");
        generateAllRemainingAttendances(verification.getAssignment().getId());

        return mapToVerificationResponse(saved);
    }

    @Override
    @Transactional
    public AssignmentVerificationResponse rejectVerification(Long verificationId, String reason,
            String approverUsername) {
        log.info("Rejecting verification: {} by user: {}", verificationId, approverUsername);

        AssignmentVerification verification = verificationRepository.findById(verificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Verification not found: " + verificationId));

        // Reset to allow more attempts
        verification.setStatus(VerificationStatus.PENDING);
        verification.setCurrentAttempts(0);

        AssignmentVerification saved = verificationRepository.save(verification);
        log.info("Rejected verification: {}, reset attempts", saved.getId());

        return mapToVerificationResponse(saved);
    }

    @Override
    public boolean requiresVerification(Assignment assignment) {
        log.info("[DEBUG] ===== requiresVerification called =====");
        log.info("[DEBUG] Assignment ID: {}, Employee ID: {}, Contract ID: {}", 
                assignment.getId(),
                assignment.getEmployee().getId(),
                assignment.getContract() != null ? assignment.getContract().getId() : "NULL");
        
        // Check condition 1: Employee is completely new (never had any assignment)
        log.info("[DEBUG] Checking condition 1: isEmployeeCompletelyNew");
        if (isEmployeeCompletelyNew(assignment.getEmployee().getId())) {
            log.info("[DEBUG] ===== RESULT: TRUE (COMPLETELY_NEW_EMPLOYEE) =====");
            log.info("Assignment {} requires verification: COMPLETELY_NEW_EMPLOYEE", assignment.getId());
            return true;
        }

        // Check condition 2: Contract setting
        log.info("[DEBUG] Checking condition 2: Contract requiresImageVerification");
        if (assignment.getContract() != null) {
            log.info("[DEBUG] Contract exists, requiresImageVerification={}", 
                    assignment.getContract().getRequiresImageVerification());
            if (Boolean.TRUE.equals(assignment.getContract().getRequiresImageVerification())) {
                log.info("[DEBUG] ===== RESULT: TRUE (CONTRACT_SETTING) =====");
                log.info("Assignment {} requires verification: CONTRACT_SETTING", assignment.getId());
                return true;
            }
        } else {
            log.info("[DEBUG] Contract is NULL");
        }

        log.info("[DEBUG] ===== RESULT: FALSE (NO VERIFICATION REQUIRED) =====");
        return false;
    }

    @Override
    public boolean isEmployeeNew(Long employeeId) {
        log.info("[DEBUG] isEmployeeNew called for employeeId={}", employeeId);
        Long completedCount = verificationRepository.countCompletedVerificationsByEmployee(employeeId);
        log.info("[DEBUG] Completed verifications count for employeeId={}: {}", employeeId, completedCount);
        boolean isNew = completedCount == 0;
        log.info("[DEBUG] isEmployeeNew result: {}", isNew);
        return isNew;
    }

    private boolean isEmployeeCompletelyNew(Long employeeId) {
        log.info("[DEBUG] isEmployeeCompletelyNew called for employeeId={}", employeeId);
        
        // Check if employee has ANY assignment (completed or not)
        Long totalAssignments = assignmentRepository.countAssignmentsByEmployee(employeeId);
        log.info("[DEBUG] Total assignments for employeeId={}: {}", employeeId, totalAssignments);
        
        boolean isNew = totalAssignments == 0;
        log.info("[DEBUG] isEmployeeCompletelyNew result: {}", isNew);
        return isNew;
    }

    @Override
    public boolean canCaptureImage(Long verificationId) {
        AssignmentVerification verification = verificationRepository.findById(verificationId)
                .orElse(null);
        
        if (verification == null) {
            return false;
        }
        
        // Kiểm tra status
        if (verification.isCompleted()) {
            log.info("Cannot capture: verification {} is already completed", verificationId);
            return false;
        }
        
        // Kiểm tra số lần
        if (verification.getCurrentAttempts() >= verification.getMaxAttempts()) {
            log.info("Cannot capture: verification {} reached max attempts {}/{}", 
                verificationId, verification.getCurrentAttempts(), verification.getMaxAttempts());
            return false;
        }
        
        // CRITICAL FIX: Kiểm tra đã chụp hôm nay chưa
        LocalDateTime today = LocalDateTime.now();
        boolean capturedToday = imageRepository.existsByVerificationIdAndCapturedDate(
            verificationId, today);
        
        if (capturedToday) {
            log.info("Cannot capture: verification {} already captured today", verificationId);
            return false;
        }
        
        log.info("Can capture: verification {} is eligible", verificationId);
        return true;
    }

    private void triggerAttendanceGeneration(Assignment assignment) {
        log.info("[DEBUG] ===== triggerAttendanceGeneration called =====");
        log.info("[DEBUG] Assignment ID: {}", assignment.getId());
        
        // CRITICAL FIX: Sinh attendance từ ngày sau ngày chụp ảnh cuối cùng
        List<LocalDateTime> captureDates = imageRepository.findCaptureDatesByAssignmentId(assignment.getId());
        log.info("[DEBUG] Found {} capture dates", captureDates.size());
        
        LocalDate startDate;
        if (!captureDates.isEmpty()) {
            // Lấy ngày chụp ảnh cuối cùng
            LocalDate lastCaptureDate = captureDates.get(0).toLocalDate();
            log.info("[DEBUG] Last capture date: {}", lastCaptureDate);
            
            // Sinh từ ngày sau ngày chụp ảnh
            startDate = lastCaptureDate.plusDays(1);
            log.info("[DEBUG] Generating attendances from day after last capture: {}", startDate);
        } else {
            // Fallback: nếu không có ảnh, sinh từ hôm nay
            startDate = LocalDate.now();
            log.warn("[DEBUG] No capture images found, generating from today: {}", startDate);
        }
        
        // FIXED: Luôn sinh attendance cho ngày tiếp theo, ngay cả khi là ngày mai
        // Chỉ không sinh nếu startDate quá xa trong tương lai (> 1 tháng)
        LocalDate today = LocalDate.now();
        LocalDate maxFutureDate = today.plusMonths(1);
        log.info("[DEBUG] Today: {}, startDate: {}, maxFutureDate: {}", today, startDate, maxFutureDate);
        
        if (!startDate.isAfter(maxFutureDate)) {
            log.info("[DEBUG] Calling generateRemainingAttendances");
            generateRemainingAttendances(assignment.getId(), startDate);
            log.info("[DEBUG] ===== Triggered attendance generation for assignment: {} from date: {} =====", 
                assignment.getId(), startDate);
        } else {
            log.info("[DEBUG] Start date {} is too far in the future, no attendances to generate yet", startDate);
        }
    }

    private void generateRemainingAttendances(Long assignmentId, LocalDate fromDate) {
        log.info("[DEBUG] ===== generateRemainingAttendances called =====");
        log.info("[DEBUG] assignmentId={}, fromDate={}", assignmentId, fromDate);

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        log.info("[DEBUG] Assignment found: id={}, workingDaysPerWeek={}", 
                assignment.getId(), assignment.getWorkingDaysPerWeek());

        // FIXED: Chỉ tạo 1 attendance cho ngày tiếp theo, không phải toàn bộ tháng
        LocalDate currentDate = fromDate;
        log.info("[DEBUG] Generating attendance for next working day starting from {}", currentDate);

        List<Attendance> attendances = new ArrayList<>();
        
        // Get working days from assignment
        List<com.company.company_clean_hub_be.entity.DayOfWeek> workingDaysEntity = new ArrayList<>();
        if (assignment.getWorkingDaysPerWeek() != null && !assignment.getWorkingDaysPerWeek().isEmpty()) {
            workingDaysEntity = (List<com.company.company_clean_hub_be.entity.DayOfWeek>) (List<?>) assignment.getWorkingDaysPerWeek();
            log.info("[DEBUG] Working days: {}", workingDaysEntity);
        } else {
            // Fallback: all days except Sunday
            workingDaysEntity = new ArrayList<>();
            workingDaysEntity.add(com.company.company_clean_hub_be.entity.DayOfWeek.MONDAY);
            workingDaysEntity.add(com.company.company_clean_hub_be.entity.DayOfWeek.TUESDAY);
            workingDaysEntity.add(com.company.company_clean_hub_be.entity.DayOfWeek.WEDNESDAY);
            workingDaysEntity.add(com.company.company_clean_hub_be.entity.DayOfWeek.THURSDAY);
            workingDaysEntity.add(com.company.company_clean_hub_be.entity.DayOfWeek.FRIDAY);
            workingDaysEntity.add(com.company.company_clean_hub_be.entity.DayOfWeek.SATURDAY);
            log.info("[DEBUG] No working days defined, using default (Mon-Sat)");
        }

        // FIXED: Chỉ tìm 1 ngày làm việc tiếp theo, không phải toàn bộ tháng
        LocalDate endDate = fromDate.plusMonths(1); // Giới hạn tìm kiếm trong 1 tháng
        boolean foundWorkingDay = false;
        
        while (!currentDate.isAfter(endDate) && !foundWorkingDay) {
            // Check if current date is a working day
            java.time.DayOfWeek currentDayOfWeek = currentDate.getDayOfWeek();
            boolean isWorkingDay = false;
            
            // Compare with working days
            for (com.company.company_clean_hub_be.entity.DayOfWeek workDay : workingDaysEntity) {
                if (workDay.name().equals(currentDayOfWeek.name())) {
                    isWorkingDay = true;
                    break;
                }
            }
            
            if (isWorkingDay) {
                // Check if attendance already exists
                boolean alreadyExists = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                        assignmentId, assignment.getEmployee().getId(), currentDate).isPresent();

                log.info("[DEBUG] Date {}: isWorkingDay=true, alreadyExists={}", currentDate, alreadyExists);

                if (!alreadyExists) {
                    Attendance attendance = Attendance.builder()
                            .assignment(assignment)
                            .employee(assignment.getEmployee())
                            .date(currentDate)
                            .workHours(java.math.BigDecimal.valueOf(8))
                            .bonus(java.math.BigDecimal.ZERO)
                            .penalty(java.math.BigDecimal.ZERO)
                            .supportCost(java.math.BigDecimal.ZERO)
                            .isOvertime(false)
                            .deleted(false)
                            .overtimeAmount(java.math.BigDecimal.ZERO)
                            .description("Tự động tạo sau khi xác minh ảnh")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    attendances.add(attendance);
                    log.info("[DEBUG] Added attendance for {}", currentDate);
                    foundWorkingDay = true; // FIXED: Dừng sau khi tìm được 1 ngày làm việc
                }
            } else {
                log.info("[DEBUG] Date {}: isWorkingDay=false ({})", currentDate, currentDayOfWeek);
            }
            currentDate = currentDate.plusDays(1);
        }

        if (!attendances.isEmpty()) {
            log.info("[DEBUG] ===== SAVING {} ATTENDANCE(S) =====", attendances.size());
            attendanceRepository.saveAll(attendances);
            log.info("[DEBUG] ===== ATTENDANCE(S) SAVED SUCCESSFULLY =====");
            log.info("Generated {} attendance(s) for assignment {}", attendances.size(), assignmentId);
        } else {
            log.info("[DEBUG] No new attendances to generate");
        }
    }

    private void generateAllRemainingAttendances(Long assignmentId) {
        log.info("[DEBUG] ===== generateAllRemainingAttendances called (for approval) =====");
        log.info("[DEBUG] assignmentId={}", assignmentId);

        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        log.info("[DEBUG] Assignment found: id={}, workingDaysPerWeek={}", 
                assignment.getId(), assignment.getWorkingDaysPerWeek());

        // Get the last capture date
        List<LocalDateTime> captureDates = imageRepository.findCaptureDatesByAssignmentId(assignmentId);
        LocalDate startDate;
        
        if (!captureDates.isEmpty()) {
            LocalDate lastCaptureDate = captureDates.get(0).toLocalDate();
            startDate = lastCaptureDate.plusDays(1);
            log.info("[DEBUG] Last capture date: {}, generating from: {}", lastCaptureDate, startDate);
        } else {
            startDate = LocalDate.now();
            log.warn("[DEBUG] No capture images found, generating from today: {}", startDate);
        }

        // Generate ALL remaining attendances for the month (not just 1)
        LocalDate currentDate = startDate;
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth()); // End of month
        log.info("[DEBUG] Generating ALL attendances from {} to {}", startDate, endDate);

        List<Attendance> attendances = new ArrayList<>();
        
        // Get working days from assignment
        List<com.company.company_clean_hub_be.entity.DayOfWeek> workingDaysEntity = new ArrayList<>();
        if (assignment.getWorkingDaysPerWeek() != null && !assignment.getWorkingDaysPerWeek().isEmpty()) {
            workingDaysEntity = (List<com.company.company_clean_hub_be.entity.DayOfWeek>) (List<?>) assignment.getWorkingDaysPerWeek();
            log.info("[DEBUG] Working days: {}", workingDaysEntity);
        } else {
            // Fallback: all days except Sunday
            workingDaysEntity = new ArrayList<>();
            workingDaysEntity.add(com.company.company_clean_hub_be.entity.DayOfWeek.MONDAY);
            workingDaysEntity.add(com.company.company_clean_hub_be.entity.DayOfWeek.TUESDAY);
            workingDaysEntity.add(com.company.company_clean_hub_be.entity.DayOfWeek.WEDNESDAY);
            workingDaysEntity.add(com.company.company_clean_hub_be.entity.DayOfWeek.THURSDAY);
            workingDaysEntity.add(com.company.company_clean_hub_be.entity.DayOfWeek.FRIDAY);
            workingDaysEntity.add(com.company.company_clean_hub_be.entity.DayOfWeek.SATURDAY);
            log.info("[DEBUG] No working days defined, using default (Mon-Sat)");
        }

        // Generate ALL remaining working days (no foundWorkingDay flag)
        while (!currentDate.isAfter(endDate)) {
            java.time.DayOfWeek currentDayOfWeek = currentDate.getDayOfWeek();
            boolean isWorkingDay = false;
            
            // Compare with working days
            for (com.company.company_clean_hub_be.entity.DayOfWeek workDay : workingDaysEntity) {
                if (workDay.name().equals(currentDayOfWeek.name())) {
                    isWorkingDay = true;
                    break;
                }
            }
            
            if (isWorkingDay) {
                // Check if attendance already exists
                boolean alreadyExists = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                        assignmentId, assignment.getEmployee().getId(), currentDate).isPresent();

                log.info("[DEBUG] Date {}: isWorkingDay=true, alreadyExists={}", currentDate, alreadyExists);

                if (!alreadyExists) {
                    Attendance attendance = Attendance.builder()
                            .assignment(assignment)
                            .employee(assignment.getEmployee())
                            .date(currentDate)
                            .workHours(java.math.BigDecimal.valueOf(8))
                            .bonus(java.math.BigDecimal.ZERO)
                            .penalty(java.math.BigDecimal.ZERO)
                            .supportCost(java.math.BigDecimal.ZERO)
                            .isOvertime(false)
                            .deleted(false)
                            .overtimeAmount(java.math.BigDecimal.ZERO)
                            .description("Tự động tạo sau khi duyệt xác minh")
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();

                    attendances.add(attendance);
                    log.info("[DEBUG] Added attendance for {}", currentDate);
                }
            } else {
                log.info("[DEBUG] Date {}: isWorkingDay=false ({})", currentDate, currentDayOfWeek);
            }
            currentDate = currentDate.plusDays(1);
        }

        if (!attendances.isEmpty()) {
            log.info("[DEBUG] ===== SAVING {} ATTENDANCE(S) AFTER APPROVAL =====", attendances.size());
            attendanceRepository.saveAll(attendances);
            log.info("[DEBUG] ===== ATTENDANCE(S) SAVED SUCCESSFULLY =====");
            log.info("Generated {} attendance(s) for assignment {} after approval", attendances.size(), assignmentId);
        } else {
            log.info("[DEBUG] No new attendances to generate");
        }
    }

    private AssignmentVerificationResponse mapToVerificationResponse(AssignmentVerification verification) {
        Assignment assignment = verification.getAssignment();
        Employee employee = assignment.getEmployee();
        log.info("assignment :{}", assignment.getId());
        log.info("employee : {}", employee.getId());
        return AssignmentVerificationResponse.builder()
                .id(verification.getId())
                .assignmentId(assignment.getId())
                .employeeId(employee.getId())
                .employeeName(employee.getName())
                .employeeCode(employee.getEmployeeCode())
                .contractId(assignment.getContract() != null ? assignment.getContract().getId() : null)
                .reason(verification.getReason())
                .status(verification.getStatus())
                .maxAttempts(verification.getMaxAttempts())
                .currentAttempts(verification.getCurrentAttempts())
                .approvedBy(verification.getApprovedBy() != null ? verification.getApprovedBy().getUsername() : null)
                .approvedAt(verification.getApprovedAt())
                .autoApprovedAt(verification.getAutoApprovedAt())
                .isCompleted(verification.isCompleted())
                .canCapture(verification.canCapture())
                .createdAt(verification.getCreatedAt())
                .updatedAt(verification.getUpdatedAt())
                .build();
    }

    private VerificationImageResponse mapToImageResponse(VerificationImage image) {
        return VerificationImageResponse.builder()
                .id(image.getId())
                .verificationId(image.getAssignmentVerification().getId())
                .employeeId(image.getEmployee().getId())
                .attendanceId(image.getAttendance() != null ? image.getAttendance().getId() : null)
                .cloudinaryPublicId(image.getCloudinaryPublicId())
                .cloudinaryUrl(image.getCloudinaryUrl())
                .latitude(image.getLatitude())
                .longitude(image.getLongitude())
                .address(image.getAddress())
                .capturedAt(image.getCapturedAt())
                .faceConfidence(image.getFaceConfidence())
                .imageQualityScore(image.getImageQualityScore())
                .createdAt(image.getCreatedAt())
                .build();
    }
    
    @Override
    @Transactional
    public void processAutoApprovals() {
        log.info("Starting auto-approval process...");
        
        List<AssignmentVerification> verificationsToApprove = 
            verificationRepository.findVerificationsForAutoApproval();
        
        log.info("Found {} verifications eligible for auto-approval", verificationsToApprove.size());
        
        for (AssignmentVerification verification : verificationsToApprove) {
            try {
                log.info("Auto-approving verification: id={}, assignmentId={}, attempts={}/{}", 
                    verification.getId(), 
                    verification.getAssignment().getId(),
                    verification.getCurrentAttempts(),
                    verification.getMaxAttempts());
                
                verification.setStatus(VerificationStatus.AUTO_APPROVED);
                verification.setAutoApprovedAt(LocalDateTime.now());
                verificationRepository.save(verification);

                // Business rule: auto-approved (max attempts reached) should also disable image verification on contract
                Contract contract = verification.getAssignment() != null ? verification.getAssignment().getContract() : null;
                if (contract != null) {
                    log.info("[DEBUG] Disabling verification for contract after auto-approval: {}", contract.getId());
                    contract.setRequiresImageVerification(false);
                    contractRepository.save(contract);
                }
                
                // FIXED: Generate ALL remaining attendances after auto-approval
                log.info("[DEBUG] Generating all remaining attendances after auto-approval");
                generateAllRemainingAttendances(verification.getAssignment().getId());
                
                log.info("Successfully auto-approved verification: {}", verification.getId());
            } catch (Exception e) {
                log.error("Failed to auto-approve verification: {}", verification.getId(), e);
            }
        }
        
        log.info("Auto-approval process completed. Approved: {}", verificationsToApprove.size());
    }

    @Override
    @Transactional
    public void syncContractVerificationState(Contract contract, boolean requiresVerification) {
        log.info("[DEBUG] syncContractVerificationState called for contract={}, requiresVerification={}", contract.getId(), requiresVerification);
        
        List<Assignment> activeAssignments = assignmentRepository.findByContractId(contract.getId()).stream()
            .filter(a -> a.getStatus() == com.company.company_clean_hub_be.entity.AssignmentStatus.IN_PROGRESS || a.getStatus() == com.company.company_clean_hub_be.entity.AssignmentStatus.SCHEDULED)
            .collect(Collectors.toList());

        LocalDateTime now = LocalDateTime.now();
        LocalDate targetDate = LocalDate.now();

        for (Assignment assignment : activeAssignments) {
            try {
                if (requiresVerification) {
                    // False -> True
                    log.info("[DEBUG] Processing False -> True for assignment {}", assignment.getId());
                    // 1. Ensure verification exists
                    AssignmentVerification verification = verificationRepository.findByAssignmentId(assignment.getId())
                            .orElseGet(() -> createVerificationRequirement(assignment, "CONTRACT_SETTING"));
                    
                    // 2. Clear future attendances
                    attendanceRepository.deleteByAssignmentIdAndDateAfter(assignment.getId(), targetDate);
                    
                    // 3. Update today's attendance (if exists)
                    Optional<Attendance> todayAttendance = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                            assignment.getId(), assignment.getEmployee().getId(), targetDate);
                    
                    if (todayAttendance.isPresent()) {
                        Attendance att = todayAttendance.get();
                        if (att.getAssignmentVerification() == null) {
                            att.setAssignmentVerification(verification);
                            attendanceRepository.save(att);
                        }
                    }
                    
                    // 4. Update assignment's workDays count
                    Long workDaysCount = attendanceRepository.countAttendancesByAssignment(assignment.getId());
                    assignment.setWorkDays(workDaysCount != null ? workDaysCount.intValue() : 0);
                    assignmentRepository.save(assignment);
                    
                } else {
                    // True -> False
                    log.info("[DEBUG] Processing True -> False for assignment {}", assignment.getId());
                    // 1. Complete pending verifications
                    verificationRepository.findByAssignmentId(assignment.getId()).ifPresent(verification -> {
                        if (verification.getStatus() == VerificationStatus.PENDING || verification.getStatus() == VerificationStatus.IN_PROGRESS) {
                            verification.setStatus(VerificationStatus.APPROVED);
                            verification.setAutoApprovedAt(now);
                            verificationRepository.save(verification);
                        }
                    });
                    
                    // 2. Generate remaining attendances
                    generateAllRemainingAttendances(assignment.getId());
                    
                    // 3. Update assignment workDays
                    Long workDaysCount = attendanceRepository.countAttendancesByAssignment(assignment.getId());
                    assignment.setWorkDays(workDaysCount != null ? workDaysCount.intValue() : 0);
                    assignmentRepository.save(assignment);
                }
            } catch (Exception e) {
                log.error("[DEBUG] Failed to sync verification state for assignment {}: {}", assignment.getId(), e.getMessage(), e);
            }
        }
    }
}