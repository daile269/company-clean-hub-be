package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.AssignmentRequest;
import com.company.company_clean_hub_be.dto.request.TemporaryReassignmentRequest;
import com.company.company_clean_hub_be.dto.response.*;
import com.company.company_clean_hub_be.entity.*;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.*;
import com.company.company_clean_hub_be.service.AssignmentMetricsService;
import com.company.company_clean_hub_be.service.AssignmentService;
import com.company.company_clean_hub_be.service.SalaryNoteValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Duration;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentServiceImpl implements AssignmentService {

        private final AssignmentRepository assignmentRepository;
        private final com.company.company_clean_hub_be.repository.CustomerAssignmentRepository customerAssignmentRepository;
        private final EmployeeRepository employeeRepository;
        private final CustomerRepository customerRepository;
        private final ContractRepository contractRepository;
        private final AttendanceRepository attendanceRepository;
        private final com.company.company_clean_hub_be.repository.RatingRepository ratingRepository;
        private final AssignmentHistoryRepository assignmentHistoryRepository;
        private final UserRepository userRepository;
        private final com.company.company_clean_hub_be.repository.DeletedAttendanceBackupRepository deletedAttendanceBackupRepository;
        private final com.company.company_clean_hub_be.repository.PayrollRepository payrollRepository;
        private final com.company.company_clean_hub_be.repository.PaymentHistoryRepository paymentHistoryRepository;
        private final com.company.company_clean_hub_be.service.NotificationService notificationService;
        private final com.company.company_clean_hub_be.service.VerificationService verificationService;
        private final com.company.company_clean_hub_be.repository.AssignmentVerificationRepository verificationRepository;
        private final com.company.company_clean_hub_be.service.WorkScheduleService workScheduleService;
        private final com.company.company_clean_hub_be.repository.WorkScheduleRepository workScheduleRepository;
        private final com.company.company_clean_hub_be.repository.VerificationImageRepository imageRepository;
        private final AssignmentMetricsService assignmentMetricsService;
        private final com.company.company_clean_hub_be.repository.SalaryNoteRepository salaryNoteRepository;
        private final com.company.company_clean_hub_be.repository.NotificationRepository notificationRepository;
        private final SalaryNoteValidator salaryNoteValidator;

        @Override
        public List<AssignmentResponse> getAllAssignments() {
                return assignmentRepository.findAll().stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public PageResponse<AssignmentResponse> getAssignmentsWithFilter(String keyword, int page, int pageSize) {
                Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
                
                String username = "anonymous";
                try {
                        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                                        .getContext().getAuthentication();
                        if (auth != null && auth.getName() != null) username = auth.getName();
                } catch (Exception ignored) {
                }
                User currentUser = userRepository.findByUsername(username).orElse(null);

                Page<Assignment> assignmentPage;
                if (currentUser != null && currentUser.getRole() != null && "QLT2".equalsIgnoreCase(currentUser.getRole().getCode())) {
                        List<Long> assignedIds = customerAssignmentRepository.findCustomerIdsByManagerId(currentUser.getId());
                        if (assignedIds.isEmpty()) {
                                return PageResponse.<AssignmentResponse>builder()
                                                .content(new ArrayList<>())
                                                .page(0)
                                                .pageSize(pageSize)
                                                .totalElements(0)
                                                .totalPages(0)
                                                .first(true)
                                                .last(true)
                                                .build();
                        }
                        assignmentPage = assignmentRepository.findByFiltersAndIds(keyword, assignedIds, pageable);
                } else {
                        assignmentPage = assignmentRepository.findByFilters(keyword, pageable);
                }

                List<AssignmentResponse> items = assignmentPage.getContent().stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());

                return PageResponse.<AssignmentResponse>builder()
                                .content(items)
                                .page(assignmentPage.getNumber())
                                .pageSize(assignmentPage.getSize())
                                .totalElements(assignmentPage.getTotalElements())
                                .totalPages(assignmentPage.getTotalPages())
                                .first(assignmentPage.isFirst())
                                .last(assignmentPage.isLast())
                                .build();
        }

        @Override
        public AssignmentResponse getAssignmentById(Long id) {
                Assignment assignment = assignmentRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

                return mapToResponse(assignment);
        }

        @Override
        @Transactional
        public AssignmentResponse createAssignment(AssignmentRequest request) {
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                log.info("createAssignment by {}: employeeId={}, contractId={}, scope={}, startDate={}",
                                username, request.getEmployeeId(), request.getContractId(), request.getScope(),
                                request.getStartDate());

                // Khai báo today một lần để reuse
                LocalDate today = LocalDate.now();

                // Nếu người tạo là Quản lý vùng (code = 'QLV') thì chỉ được phân công từ hôm
                // nay trở về sau
                User creator = userRepository.findByUsername(username)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
                if (creator.getRole() != null && "QLV".equalsIgnoreCase(creator.getRole().getCode())) {
                        if (request.getStartDate().isBefore(today)) {
                                log.warn("QLV cannot create assignment with startDate in the past: {}",
                                                request.getStartDate());
                                throw new AppException(ErrorCode.FORBIDDEN);
                        }
                        // Nếu tạo cho ngày hôm nay: chỉ được phân công đến tối đa 1 tiếng SAU giờ bắt đầu làm của hợp đồng
                        // Ví dụ: giờ làm 17:00 → được phân đến trước 18:00
                        // Nếu không tìm thấy hợp đồng/giờ làm → bỏ qua, không chặn
                        if (request.getStartDate().isEqual(today)) {
                                LocalTime now = LocalTime.now();
                                LocalTime workStartTime = null;
                                if (request.getContractId() != null) {
                                        workStartTime = contractRepository.findById(request.getContractId())
                                                        .map(c -> c.getWorkStartTime())
                                                        .orElse(null);
                                }
                                if (workStartTime != null) {
                                        LocalTime cutoffTime = workStartTime.plusHours(1);
                                        if (now.isAfter(cutoffTime)) {
                                                log.warn("QLV không được phân công hôm nay sau thời hạn cho phép: now={}, cutoffTime={}, workStartTime={}",
                                                                now, cutoffTime, workStartTime);
                                                String detail = String.format(
                                                        "QLV chỉ được phân công/điều động trong vòng 1 tiếng kể từ khi ca làm bắt đầu.%nCa làm: %s — Hạn chót phân công: %s — Thời điểm hiện tại: %s",
                                                        workStartTime.toString(), cutoffTime.toString(), now.withSecond(0).withNano(0).toString());
                                                throw new AppException(ErrorCode.QLV_CREATE_AFTER_ALLOWED_TIME, detail);
                                        }
                                }
                        }
                }

                // Parse assignmentType safely (default to FIXED_BY_CONTRACT)
                AssignmentType assignmentTypeParsed;
                String at = request.getAssignmentType();
                if (at == null || at.isBlank()) {
                        assignmentTypeParsed = AssignmentType.FIXED_BY_CONTRACT;
                } else {
                        try {
                                assignmentTypeParsed = AssignmentType.valueOf(at);
                        } catch (IllegalArgumentException ex) {
                                log.warn("Invalid assignmentType '{}', defaulting to FIXED_BY_CONTRACT", at);
                                assignmentTypeParsed = AssignmentType.FIXED_BY_CONTRACT;
                        }
                }

                // Business rule: Only "Quản lý tổng" (QLT1 and QLT2) can create SUPPORT assignments
                if (assignmentTypeParsed == AssignmentType.SUPPORT) {
                        String roleCode = (creator.getRole() != null) ? creator.getRole().getCode() : "";
                        if (!"QLT1".equalsIgnoreCase(roleCode) && !"QLT2".equalsIgnoreCase(roleCode)) {
                                log.warn("User '{}' with role '{}' attempted to create SUPPORT assignment - forbidden",
                                                username, roleCode);
                                throw new AppException(ErrorCode.FORBIDDEN);
                        }
                }

                Employee employee = employeeRepository.findById(request.getEmployeeId())
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

                AssignmentScope scope = request.getScope() != null ? request.getScope() : AssignmentScope.CONTRACT;
                Contract contract = null;
                List<java.time.DayOfWeek> workingDays = null;

                // Xử lý theo scope
                if (scope == AssignmentScope.CONTRACT) {
                        // CONTRACT scope: require contract, get workingDaysPerWeek from contract
                        if (request.getContractId() == null) {
                                throw new AppException(ErrorCode.CONTRACT_NOT_FOUND);
                        }
                        contract = contractRepository.findById(request.getContractId())
                                        .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

                        // Kiểm tra ngày bắt đầu assignment không được trước ngày bắt đầu contract
                        if (request.getStartDate().isBefore(contract.getStartDate())) {
                                throw new AppException(ErrorCode.ASSIGNMENT_START_DATE_BEFORE_CONTRACT);
                        }

                        // Kiểm tra hợp đồng đã hết hạn trong quá khứ chưa
                        if (contract.getEndDate() != null && contract.getEndDate().isBefore(today)) {
                                throw new AppException(ErrorCode.CONTRACT_EXPIRED);
                        }

                        workingDays = contract.getWorkingDaysPerWeek() != null
                                        ? new ArrayList<>(contract.getWorkingDaysPerWeek())
                                        : null;

                        // If contract declares a fixed number of employees, ensure new assignment
                        // won't exceed that number (exclude SUPPORT assignments from the count)
                        Integer maxPositions = contract.getNumberOfEmployees();
                        // Business rule: SUPPORT assignments are NOT bound by headcount limits
                        if (maxPositions != null && assignmentTypeParsed != AssignmentType.SUPPORT) {
                                // Count distinct active employees for this contract up to the startDate,
                                // excluding SUPPORT assignments (SUPPORT does not occupy a contract slot)
                                Long currentCount = assignmentRepository
                                                .countDistinctActiveEmployeesByContractBeforeExcludingType(
                                                                contract.getId(), request.getStartDate(),
                                                                com.company.company_clean_hub_be.entity.AssignmentType.SUPPORT);

                                // If the employee we're assigning is already among active assignments,
                                // do not treat them as increasing the headcount
                                boolean employeeAlreadyCounted = assignmentRepository
                                                .findActiveAssignmentByEmployeeAndContract(request.getEmployeeId(),
                                                                request.getContractId())
                                                .stream()
                                                .anyMatch(a -> a.getStatus() == com.company.company_clean_hub_be.entity.AssignmentStatus.IN_PROGRESS
                                                                || a.getStatus() == com.company.company_clean_hub_be.entity.AssignmentStatus.SCHEDULED);

                                if (!employeeAlreadyCounted && currentCount != null
                                                && currentCount >= maxPositions) {
                                        log.warn("Attempt to create assignment would exceed contract positions: contractId={}, max={}, current={}",
                                                        contract.getId(), maxPositions, currentCount);
                                        throw new AppException(ErrorCode.CONTRACT_POSITIONS_EXCEEDED);
                                }
                        }
                } else {
                        // COMPANY scope: contract is null, workingDaysPerWeek from request
                        workingDays = request.getWorkingDaysPerWeek() != null
                                        ? new ArrayList<>(request.getWorkingDaysPerWeek())
                                        : null;
                }

                // Tự động xác định status dựa vào startDate
                AssignmentStatus finalStatus;

                if (request.getStartDate().isAfter(today)) {
                        // Phân công trong tương lai -> SCHEDULED (không tạo attendance ngay)
                        finalStatus = AssignmentStatus.SCHEDULED;
                        log.info("Assignment startDate {} is in future, set status to SCHEDULED",
                                        request.getStartDate());
                } else {
                        // Phân công từ hôm nay trở về trước -> lấy từ request hoặc mặc định IN_PROGRESS
                        finalStatus = request.getStatus() != null ? request.getStatus() : AssignmentStatus.IN_PROGRESS;
                }

                // Kiểm tra nhân viên đã được phân công phụ trách hợp đồng này chưa (Chặn trùng
                // cho cả IN_PROGRESS và SCHEDULED, loại trừ loại phân công SUPPORT)
                if (scope == AssignmentScope.CONTRACT && assignmentTypeParsed != AssignmentType.SUPPORT
                                && (AssignmentStatus.IN_PROGRESS.equals(finalStatus)
                                || AssignmentStatus.SCHEDULED.equals(finalStatus))) {
                        List<Assignment> existingAssignments = assignmentRepository
                                        .findActiveAssignmentByEmployeeAndContract(request.getEmployeeId(),
                                                        request.getContractId());
                        if (!existingAssignments.isEmpty()) {
                                throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS);
                        }
                }

                // R1/R2: validate Salary Note của hợp đồng trước khi lưu (chặn sai loại / lương ngoài khoảng)
                if (contract != null) {
                        salaryNoteValidator.validateAssignmentType(contract, assignmentTypeParsed);
                        salaryNoteValidator.validateSalaryRange(contract, assignmentTypeParsed, request.getSalaryAtTime());
                }

                Assignment assignment = Assignment.builder()
                                .employee(employee)
                                .contract(contract)
                                .scope(scope)
                                .startDate(request.getStartDate())
                                .endDate(calculateEndDate(request, assignmentTypeParsed)) // Thêm logic tính endDate
                                .status(finalStatus)
                                .salaryAtTime(request.getSalaryAtTime())
                                .workingDaysPerWeek(workingDays)
                                .additionalAllowance(request.getAdditionalAllowance())
                                .monthlySupport(request.getMonthlySupport())
                                .advanceNote(request.getAdvanceNote())
                                .description(request.getDescription())
                                .assignmentType(assignmentTypeParsed)
                                .assignedBy(creator)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                Assignment savedAssignment = assignmentRepository.save(assignment);

                log.info("[DEBUG] Assignment saved: id={}, employee={}, contract={}, status={}, startDate={}, endDate={}",
                                savedAssignment.getId(),
                                savedAssignment.getEmployee().getId(),
                                savedAssignment.getContract() != null ? savedAssignment.getContract().getId() : null,
                                finalStatus,
                                request.getStartDate(),
                                savedAssignment.getEndDate());

                // CRITICAL FIX: Kiểm tra verification TRƯỚC khi sinh attendance
                boolean requiresVerification = false;
                String verificationReason = null;
                log.info("[DEBUG] ===== STARTING VERIFICATION CHECK =====");
                log.info("[DEBUG] Assignment ID: {}, Employee ID: {}, Contract ID: {}",
                                savedAssignment.getId(),
                                savedAssignment.getEmployee().getId(),
                                savedAssignment.getContract() != null ? savedAssignment.getContract().getId() : "NULL");

                try {
                        log.info("[DEBUG] Calling verificationService.requiresVerification()...");
                        requiresVerification = verificationService.requiresVerification(savedAssignment);
                        log.info("[DEBUG] ===== VERIFICATION CHECK RESULT: {} =====", requiresVerification);
                        log.info("[DEBUG] Verification check: assignmentId={}, requiresVerification={}",
                                        savedAssignment.getId(), requiresVerification);

                        if (requiresVerification) {
                                // Ưu tiên 1: Kiểm tra nhân viên đang dở dang xác minh nhân viên mới (đã chụp 1-4 lần)
                                // Trường hợp này xảy ra khi nhân viên đã chụp 1-4 lần ở assignment trước (ví dụ: hỗ trợ 1 ngày)
                                // rồi được tạo assignment mới. Dù có nhiều assignment, vẫn phải tiếp tục xác minh nhân viên mới.
                                Long verifiedNewEmployeeCount = workScheduleRepository.countVerifiedSchedulesByEmployeeAndReason(
                                                savedAssignment.getEmployee().getId(), WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION);
                                boolean isInProgressNewEmployeeVerification = verifiedNewEmployeeCount > 0 && verifiedNewEmployeeCount < 5;

                                if (isInProgressNewEmployeeVerification) {
                                        // Đang dở dang xác minh nhân viên mới → luôn là NEW_EMPLOYEE
                                        verificationReason = "NEW_EMPLOYEE";
                                        log.info("[DEBUG] Verification required: assignmentId={}, reason=NEW_EMPLOYEE (in-progress, verifiedCount={}/5)",
                                                        savedAssignment.getId(), verifiedNewEmployeeCount);
                                } else {
                                        // Ưu tiên 2: Dùng logic đếm assignment khác (loại trừ assignment vừa tạo)
                                        // KHÔNG dùng verificationService.isEmployeeNew() vì method đó dùng logic khác (đếm verification đã duyệt)
                                        Long otherAssignments = assignmentRepository.countAssignmentsByEmployeeExcluding(
                                                        savedAssignment.getEmployee().getId(), savedAssignment.getId());
                                        boolean isNewEmployee = otherAssignments == 0;
                                        verificationReason = isNewEmployee ? "NEW_EMPLOYEE" : "CONTRACT_SETTING";
                                        log.info("[DEBUG] Verification required: assignmentId={}, reason={}, isNewEmployee={}, otherAssignments={}",
                                                        savedAssignment.getId(), verificationReason, isNewEmployee, otherAssignments);
                                }
                        } else {
                                log.info("[DEBUG] Verification NOT required - will generate all attendances normally");
                        }
                } catch (Exception e) {
                        log.error("[DEBUG] Error checking verification: {}", e.getMessage(), e);
                        requiresVerification = false;
                }

                // Tự động tạo chấm công cho cả SCHEDULED và IN_PROGRESS
                // Cron sau này chỉ chuyển status, không tạo attendance nữa
                log.info("[DEBUG] ===== ATTENDANCE GENERATION PHASE =====");
                log.info("[DEBUG] finalStatus={}, IN_PROGRESS={}, SCHEDULED={}",
                                finalStatus, AssignmentStatus.IN_PROGRESS, AssignmentStatus.SCHEDULED);

                if (AssignmentStatus.IN_PROGRESS.equals(finalStatus)
                                || AssignmentStatus.SCHEDULED.equals(finalStatus)) {

                        log.info("[DEBUG] ===== ENTERING ATTENDANCE GENERATION BLOCK =====");
                        log.info("[DEBUG] Creating attendances: assignmentId={}, requiresVerification={}, assignmentType={}",
                                        savedAssignment.getId(), requiresVerification, assignmentTypeParsed);

                        // Nếu cần verification: KHÔNG tạo attendance, chỉ tạo work_schedules
                        if (requiresVerification) {
                                log.info("[DEBUG] ===== VERIFICATION REQUIRED PATH - Creating work_schedules only =====");

                                // Tạo verification requirement nếu là nhân viên mới
                                AssignmentVerification verification = null;
                                try {
                                        verification = verificationService.createVerificationRequirement(
                                                        savedAssignment, verificationReason);
                                        log.info("[DEBUG] Created verification requirement: assignmentId={}, reason={}, verificationId={}",
                                                        savedAssignment.getId(), verificationReason,
                                                        verification != null ? verification.getId() : null);
                                } catch (Exception e) {
                                        log.error("[DEBUG] Error creating verification: {}", e.getMessage(), e);
                                }

                                // Tính end date cho work_schedules
                                // Priority 1: assignment.endDate (SUPPORT worker với ngày cụ thể)
                                // Priority 2: contract.endDate
                                // Priority 3: cuối tháng
                                LocalDate endDate;
                                if (savedAssignment.getEndDate() != null) {
                                        endDate = savedAssignment.getEndDate();
                                        log.info("[DEBUG] Using assignment.endDate for work_schedules: {}", endDate);
                                } else {
                                        YearMonth yearMonth = YearMonth.from(request.getStartDate());
                                        endDate = yearMonth.atEndOfMonth();
                                        if (contract != null && contract.getEndDate() != null
                                                        && contract.getEndDate().isBefore(endDate)) {
                                                endDate = contract.getEndDate();
                                        }
                                        log.info("[DEBUG] Using contract/month endDate for work_schedules: {}", endDate);
                                }

                                // Xác định reason
                                boolean isNewEmployee = "NEW_EMPLOYEE".equals(verificationReason);
                                WorkScheduleReason wsReason = isNewEmployee
                                                ? WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION
                                                : WorkScheduleReason.CONTRACT_REQUIREMENT;

                                // Tạo work_schedules thay vì attendances
                                // SUPPORT: tạo đúng theo từng ngày trong request.getDates()
                                // NEW_EMPLOYEE + transitionToContractMode: 5 ngày đầu = NEW_EMPLOYEE_VERIFICATION, còn lại = CONTRACT_REQUIREMENT
                                // CONTRACT_REQUIREMENT only: toàn bộ = CONTRACT_REQUIREMENT
                                try {
                                        if (assignmentTypeParsed == AssignmentType.SUPPORT
                                                        && request.getDates() != null
                                                        && !request.getDates().isEmpty()) {
                                                // SUPPORT: tạo đúng theo ngày được chọn
                                                workScheduleService.createWorkSchedulesForDates(
                                                                savedAssignment,
                                                                wsReason,
                                                                verification != null ? verification.getId() : null,
                                                                request.getDates());
                                                log.info("[DEBUG] Created work_schedules for SUPPORT assignmentId={} on dates={}",
                                                                savedAssignment.getId(), request.getDates());

                                        } else if (isNewEmployee) {
                                                // NEW_EMPLOYEE: chỉ N ngày làm việc đầu tiên = NEW_EMPLOYEE_VERIFICATION (N = maxAttempts còn thiếu)
                                                int maxAttempts = verification != null ? verification.getMaxAttempts() : 5;
                                                
                                                // Verification bắt đầu từ MAX(assignmentStartDate, today)
                                                // Nhân viên chỉ cần verify từ hôm nay trở đi (không thể verify quá khứ)
                                                LocalDate verificationStartDate = request.getStartDate().isAfter(today) 
                                                                ? request.getStartDate() 
                                                                : today;
                                                
                                                // Giới hạn verification trong tháng hiện tại để tránh sinh công sang tháng sau
                                                // Monthly scheduler sẽ xử lý tháng sau
                                                LocalDate endOfCurrentMonth = today.withDayOfMonth(today.lengthOfMonth());
                                                
                                                // Tính endDate cho verification period
                                                // Bắt đầu từ endDate đã tính ở trên (có thể là cuối tháng của startDate)
                                                LocalDate verificationEndDate = endDate;
                                                
                                                // CRITICAL: Nếu verificationStartDate đã được điều chỉnh sang tháng sau (today > startDate),
                                                // thì verificationEndDate cũng phải được điều chỉnh để không nhỏ hơn verificationStartDate
                                                if (verificationEndDate.isBefore(verificationStartDate)) {
                                                        verificationEndDate = endOfCurrentMonth;
                                                        log.info("[DEBUG] Adjusted verificationEndDate to current month end: {} (was {}, verificationStartDate={})",
                                                                        endOfCurrentMonth, endDate, verificationStartDate);
                                                }
                                                
                                                // Ưu tiên 1: Respect assignment endDate nếu có (cho SUPPORT assignments)
                                                // Ưu tiên 2: Giới hạn trong tháng hiện tại
                                                // Ưu tiên 3: Respect contract endDate
                                                if (verificationEndDate.isAfter(endOfCurrentMonth)) {
                                                        verificationEndDate = endOfCurrentMonth;
                                                        log.info("[DEBUG] Limited verification period to end of current month: {} (assignment endDate was {})",
                                                                        endOfCurrentMonth, endDate);
                                                }
                                                
                                                if (contract != null && contract.getEndDate() != null 
                                                                && contract.getEndDate().isBefore(verificationEndDate)) {
                                                        verificationEndDate = contract.getEndDate();
                                                        log.info("[DEBUG] Limited verification period to contract endDate: {}", contract.getEndDate());
                                                }
                                                
                                                // Tạo verification dates trong giới hạn đã tính
                                                List<LocalDate> verificationDates = getFirstNWorkingDays(
                                                                verificationStartDate, verificationEndDate,
                                                                savedAssignment.getWorkingDaysPerWeek(), maxAttempts);

                                                workScheduleService.createWorkSchedulesForDates(
                                                                savedAssignment,
                                                                WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION,
                                                                verification != null ? verification.getId() : null,
                                                                verificationDates);
                                                log.info("[DEBUG] Created {} NEW_EMPLOYEE_VERIFICATION work_schedules for assignmentId={} " +
                                                                "(limited to current month, startDate={}, endDate={}, dates={})",
                                                                verificationDates.size(), savedAssignment.getId(), 
                                                                verificationStartDate, verificationEndDate, verificationDates);

                                                // Kiểm tra hợp đồng có bật verification không
                                                boolean transitionToContract = verification != null
                                                                && Boolean.TRUE.equals(verification.getTransitionToContractMode());
                                                
                                                if (!verificationDates.isEmpty()) {
                                                        LocalDate afterVerification = verificationDates
                                                                        .get(verificationDates.size() - 1)
                                                                        .plusDays(1);
                                                        if (!afterVerification.isAfter(endDate)) {
                                                                if (transitionToContract) {
                                                                        // Hợp đồng BẬT verification → tạo work_schedules CONTRACT_REQUIREMENT
                                                                        workScheduleService.createWorkSchedulesForAssignment(
                                                                                        savedAssignment,
                                                                                        WorkScheduleReason.CONTRACT_REQUIREMENT,
                                                                                        null,
                                                                                        afterVerification,
                                                                                        endDate);
                                                                        log.info("[DEBUG] Created CONTRACT_REQUIREMENT work_schedules from {} to {} for assignmentId={}",
                                                                                        afterVerification, endDate, savedAssignment.getId());
                                                                } else {
                                                                        // Hợp đồng KHÔNG bật verification → KHÔNG tạo AUTO_ATTENDANCE WorkSchedules
                                                                        // handleVerificationApproval() sẽ tạo attendance trực tiếp sau khi verification hoàn tất
                                                                        log.info("[DEBUG] Skipping AUTO_ATTENDANCE work_schedules for non-verification contract. " +
                                                                                        "handleVerificationApproval() will create attendance directly. " +
                                                                                        "assignmentId={}, afterVerification={}, endDate={}",
                                                                                        savedAssignment.getId(), afterVerification, endDate);
                                                                }
                                                        }
                                                }

                                        } else {
                                                // CONTRACT_REQUIREMENT only (nhân viên cũ, hợp đồng bật verifi)
                                                workScheduleService.createWorkSchedulesForAssignment(
                                                                savedAssignment,
                                                                WorkScheduleReason.CONTRACT_REQUIREMENT,
                                                                null,
                                                                request.getStartDate(),
                                                                endDate);
                                                log.info("[DEBUG] Created CONTRACT_REQUIREMENT work_schedules for assignmentId={} from {} to {}",
                                                                savedAssignment.getId(), request.getStartDate(), endDate);
                                        }
                                } catch (Exception e) {
                                        log.error("[DEBUG] Error creating work_schedules: {}", e.getMessage(), e);
                                }

                                // NOTE: Gap period attendance (contractStartDate to assignmentStartDate - 1)
                                // will be created in handleVerificationApproval() AFTER verification is approved/bypassed.
                                // Nhân viên mới cần verification trước — chưa duyệt thì chưa tạo attendance.

                                // plannedDays = số ngày trong dates (SUPPORT) hoặc ngày làm việc theo lịch
                                if (assignmentTypeParsed == AssignmentType.SUPPORT
                                                && request.getDates() != null
                                                && !request.getDates().isEmpty()) {
                                        savedAssignment.setPlannedDays(request.getDates().size());
                                        savedAssignment.setWorkDays(0);
                                        assignmentRepository.save(savedAssignment);
                                } else if (workingDays != null && !workingDays.isEmpty()) {
                                        // plannedDays phải tính từ đầu tháng (không phải từ assignmentStartDate)
                                        // để phản ánh đúng số ngày làm việc theo lịch hợp đồng trong tháng
                                        YearMonth yearMonth = YearMonth.from(request.getStartDate());
                                        LocalDate monthStart = yearMonth.atDay(1);
                                        int planned = countWorkingDaysBetween(workingDays, monthStart, endDate);
                                        savedAssignment.setPlannedDays(planned);
                                        savedAssignment.setWorkDays(0);
                                        assignmentRepository.save(savedAssignment);
                                }
                        } else {
                                // Không cần verification - sinh toàn bộ attendance như bình thường
                                log.info("[DEBUG] ===== NO VERIFICATION REQUIRED PATH - Creating all attendances normally =====");
                                log.info("[DEBUG] NO VERIFICATION REQUIRED - Creating all attendances normally");
                                log.info("[DEBUG] ===== IMPORTANT: NO WorkSchedule should be created in this path =====");
                                
                                // Cleanup: Xóa các WorkSchedule cũ của nhân viên này (từ assignments trước)
                                // vì nhân viên đã hoàn thành verification, không cần chụp ảnh nữa
                                try {
                                        List<WorkSchedule> oldSchedules = workScheduleRepository
                                                .findByEmployeeIdAndDateRange(
                                                        savedAssignment.getEmployee().getId(),
                                                        LocalDate.now(),
                                                        LocalDate.now().plusYears(1))
                                                .stream()
                                                .filter(ws -> ws.getStatus() == WorkScheduleStatus.SCHEDULED || 
                                                             ws.getStatus() == WorkScheduleStatus.MISSED)
                                                .filter(ws -> !ws.getAssignment().getId().equals(savedAssignment.getId()))
                                                .collect(Collectors.toList());
                                        
                                        if (!oldSchedules.isEmpty()) {
                                                log.info("[DEBUG] Found {} old SCHEDULED/MISSED WorkSchedules from previous assignments for employee {}", 
                                                        oldSchedules.size(), savedAssignment.getEmployee().getId());
                                                oldSchedules.forEach(ws -> log.info("[DEBUG]   - Deleting old WorkSchedule: id={}, assignmentId={}, date={}, status={}", 
                                                        ws.getId(), ws.getAssignment().getId(), ws.getScheduledDate(), ws.getStatus()));
                                                workScheduleRepository.deleteAll(oldSchedules);
                                                log.info("[DEBUG] Deleted {} old WorkSchedules for employee {} (verification completed)", 
                                                        oldSchedules.size(), savedAssignment.getEmployee().getId());
                                        } else {
                                                log.info("[DEBUG] No old WorkSchedules to clean up for employee {}", 
                                                        savedAssignment.getEmployee().getId());
                                        }
                                } catch (Exception e) {
                                        log.error("[DEBUG] Error cleaning up old WorkSchedules: {}", e.getMessage(), e);
                                }

                                // Nếu là SUPPORT: tạo chấm công theo danh sách ngày được gửi trong request
                                if (assignmentTypeParsed == AssignmentType.SUPPORT) {
                                        log.info("[DEBUG] Assignment type is SUPPORT");
                                        List<java.time.LocalDate> requestedDates = request.getDates();
                                        if (requestedDates != null && !requestedDates.isEmpty()) {
                                                List<Attendance> toSave = new ArrayList<>();
                                                for (java.time.LocalDate d : requestedDates) {
                                                        boolean alreadyExists = attendanceRepository
                                                                        .findByAssignmentAndEmployeeAndDate(
                                                                                        savedAssignment.getId(),
                                                                                        savedAssignment.getEmployee()
                                                                                                        .getId(),
                                                                                        d)
                                                                        .isPresent();
                                                        if (!alreadyExists) {
                                                                Attendance att = Attendance.builder()
                                                                                .employee(savedAssignment.getEmployee())
                                                                                .assignment(savedAssignment)
                                                                                .date(d)
                                                                                .workHours(java.math.BigDecimal
                                                                                                .valueOf(8))
                                                                                .deleted(false)
                                                                                .bonus(java.math.BigDecimal.ZERO)
                                                                                .penalty(java.math.BigDecimal.ZERO)
                                                                                .supportCost(java.math.BigDecimal.ZERO)
                                                                                .isOvertime(false)
                                                                                .overtimeAmount(java.math.BigDecimal.ZERO)
                                                                                .description(request
                                                                                                .getDescription() != null
                                                                                                                ? request.getDescription()
                                                                                                                : "Tự động tạo từ phân công (SUPPORT)")
                                                                                .createdAt(LocalDateTime.now())
                                                                                .updatedAt(LocalDateTime.now())
                                                                                .build();
                                                                toSave.add(att);
                                                        }
                                                }

                                                if (!toSave.isEmpty()) {
                                                        attendanceRepository.saveAll(toSave);
                                                        int created = toSave.size();
                                                        savedAssignment.setWorkDays(
                                                                        (savedAssignment.getWorkDays() == null ? 0
                                                                                        : savedAssignment.getWorkDays())
                                                                                        + created);
                                                        savedAssignment.setPlannedDays(
                                                                        (savedAssignment.getPlannedDays() == null ? 0
                                                                                        : savedAssignment
                                                                                                        .getPlannedDays())
                                                                                        + created);
                                                        assignmentRepository.save(savedAssignment);
                                                        log.info("[DEBUG] Created {} support attendances for assignmentId={}",
                                                                        created,
                                                                        savedAssignment.getId());
                                                }
                                        }
                                } else if (workingDays != null && !workingDays.isEmpty()) {
                                        log.info("[DEBUG] Assignment type is not SUPPORT, generating regular attendances");
                                        // Nếu startDate trong quá khứ, tạo assignment và attendance cho các tháng từ
                                        // startDate đến hiện tại
                                        YearMonth startMonth = YearMonth.from(request.getStartDate());
                                        YearMonth currentMonth = YearMonth.from(today);

                                        if (startMonth.isBefore(currentMonth)) {
                                                log.info("[DEBUG] StartDate {} is in the past. Creating assignments and attendances from {} to {}",
                                                                request.getStartDate(), startMonth, currentMonth);

                                                // Tạo attendance cho tháng đầu tiên (savedAssignment đã được tạo ở
                                                // trên)
                                                autoGenerateAttendancesForAssignment(savedAssignment,
                                                                request.getStartDate());

                                                // Tạo assignment và attendance cho các tháng tiếp theo (từ tháng sau
                                                // startMonth
                                                // đến currentMonth)
                                                YearMonth nextMonth = startMonth.plusMonths(1);
                                                while (!nextMonth.isAfter(currentMonth)) {
                                                        LocalDate monthStartDate = nextMonth.atDay(1);

                                                        // Kiểm tra đã có assignment cho tháng này chưa
                                                        Optional<Assignment> existingMonthAssignment = assignmentRepository
                                                                        .findByEmployeeAndContractAndMonth(
                                                                                        request.getEmployeeId(),
                                                                                        request.getContractId(),
                                                                                        nextMonth.getYear(),
                                                                                        nextMonth.getMonthValue());

                                                        if (existingMonthAssignment.isEmpty()) {
                                                                // Tạo assignment mới cho tháng này
                                                                Assignment monthlyAssignment = Assignment.builder()
                                                                                .employee(employee)
                                                                                .contract(contract)
                                                                                .scope(scope)
                                                                                .startDate(monthStartDate)
                                                                                .status(AssignmentStatus.IN_PROGRESS) // Các
                                                                                                                      // tháng
                                                                                                                      // trong
                                                                                                                      // quá
                                                                                                                      // khứ
                                                                                                                      // luôn
                                                                                                                      // là
                                                                                                                      // IN_PROGRESS
                                                                                .salaryAtTime(request.getSalaryAtTime())
                                                                                .workingDaysPerWeek(workingDays != null
                                                                                                ? new ArrayList<>(
                                                                                                                workingDays)
                                                                                                : null)
                                                                                .additionalAllowance(request
                                                                                                .getAdditionalAllowance())
                                                                                .monthlySupport(request.getMonthlySupport())
                                                                                .advanceNote(request.getAdvanceNote())
                                                                                .description(request.getDescription())
                                                                                .assignmentType(assignmentTypeParsed)
                                                                                .assignedBy(savedAssignment
                                                                                                .getAssignedBy())
                                                                                .createdAt(LocalDateTime.now())
                                                                                .updatedAt(LocalDateTime.now())
                                                                                .build();

                                                                Assignment savedMonthlyAssignment = assignmentRepository
                                                                                .save(monthlyAssignment);
                                                                log.info("[DEBUG] Created monthly assignment for {}/{}: assignmentId={}",
                                                                                nextMonth.getMonthValue(),
                                                                                nextMonth.getYear(),
                                                                                savedMonthlyAssignment.getId());

                                                                // Tạo attendance cho tháng này
                                                                autoGenerateAttendancesForAssignment(
                                                                                savedMonthlyAssignment,
                                                                                monthStartDate);
                                                        } else {
                                                                log.info("[DEBUG] Assignment already exists for employee={}, contract={}, month={}/{}",
                                                                                request.getEmployeeId(),
                                                                                request.getContractId(),
                                                                                nextMonth.getMonthValue(),
                                                                                nextMonth.getYear());
                                                        }

                                                        nextMonth = nextMonth.plusMonths(1);
                                                }
                                        } else {
                                                // StartDate là tháng hiện tại hoặc tương lai - chỉ tạo cho tháng đó
                                                log.info("[DEBUG] StartDate {} is current or future month, creating attendances for this month only",
                                                                request.getStartDate());
                                                autoGenerateAttendancesForAssignment(savedAssignment,
                                                                request.getStartDate());
                                        }
                                }
                        }
                }

                log.info("createAssignment completed by {}: assignmentId={} (employee={}, contract={})",
                                username,
                                savedAssignment.getId(),
                                savedAssignment.getEmployee().getId(),
                                savedAssignment.getContract() != null ? savedAssignment.getContract().getId() : null);

                // Kỳm tra và gửi notification nếu trùng khung giờ (không chặn flow chính)
                if (contract != null) {
                        try {
                                checkAndNotifyTimeConflict(savedAssignment, contract);
                        } catch (Exception e) {
                                log.warn("checkAndNotifyTimeConflict failed for assignmentId={}: {}",
                                                savedAssignment.getId(), e.getMessage());
                        }
                }

                // Task 19: Kiểm tra và gửi notification thiếu nhân viên (không chặn flow chính)
                if (contract != null) {
                        try {
                                checkAndNotifyInsufficientStaff(contract);
                        } catch (Exception e) {
                                log.warn("checkAndNotifyInsufficientStaff failed for contractId={}: {}",
                                                contract.getId(), e.getMessage());
                        }
                }

                // Task 19: Kiểm tra và gửi notification phân công vượt salaryNote (không chặn flow chính)
                if (contract != null) {
                        try {
                                checkAndNotifyAssignmentOverBudget(savedAssignment);
                        } catch (Exception e) {
                                log.warn("checkAndNotifyAssignmentOverBudget failed for assignmentId={}: {}",
                                                savedAssignment.getId(), e.getMessage());
                        }
                }

                log.info("[DEBUG] ===== createAssignment COMPLETED =====");
                log.info("[DEBUG] Final result: assignmentId={}, requiresVerification={}, attendancesCreated={}",
                                savedAssignment.getId(), requiresVerification,
                                savedAssignment.getWorkDays() != null ? savedAssignment.getWorkDays() : 0);
                
                // DEBUG: Kiểm tra WorkSchedule được tạo
                List<WorkSchedule> workSchedules = workScheduleRepository.findByAssignmentId(savedAssignment.getId());
                log.info("[DEBUG] ===== WORK SCHEDULE CHECK =====");
                log.info("[DEBUG] WorkSchedule count for assignmentId={}: {}", savedAssignment.getId(), workSchedules.size());
                if (!workSchedules.isEmpty()) {
                        workSchedules.forEach(ws -> log.info("[DEBUG]   - WorkSchedule: id={}, date={}, status={}, reason={}", 
                                ws.getId(), ws.getScheduledDate(), ws.getStatus(), ws.getReason()));
                        log.warn("[DEBUG] ===== WARNING: WorkSchedule created but requiresVerification=false! =====");
                } else {
                        log.info("[DEBUG] No WorkSchedule created (correct for requiresVerification=false)");
                }

                return mapToResponse(savedAssignment);
        }

        @Override
        @Transactional
        public AssignmentResponse updateAssignment(Long id, AssignmentRequest request) {

                log.info("[ASSIGNMENT][UPDATE] Start update assignment, id={}, request={}", id, request);

                if (id == null) {
                        log.error("[ASSIGNMENT][UPDATE] Assignment id is null");
                        throw new IllegalArgumentException("Assignment id must not be null");
                }

                Assignment assignment = assignmentRepository.findById(id)
                                .orElseThrow(() -> {
                                        log.error("[ASSIGNMENT][UPDATE] Assignment not found, id={}", id);
                                        return new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND);
                                });

                log.debug("[ASSIGNMENT][UPDATE] Found assignment id={}, currentStatus={}",
                                assignment.getId(), assignment.getStatus());

                // Nếu người cập nhật là Quản lý vùng (code = 'QLV') thì chỉ được cập nhật
                // assignment từ hôm nay trở về sau
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User updater = userRepository.findByUsername(username)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
                if (updater.getRole() != null && "QLV".equalsIgnoreCase(updater.getRole().getCode())) {
                        LocalDate today = LocalDate.now();

                        // Kiểm tra startDate của assignment hiện tại
                        if (assignment.getStartDate().isBefore(today)) {
                                log.warn("QLV cannot update assignment with startDate in the past: {}",
                                                assignment.getStartDate());
                                throw new AppException(ErrorCode.FORBIDDEN);
                        }

                        // Kiểm tra startDate mới (nếu có thay đổi)
                        if (request.getStartDate() != null && request.getStartDate().isBefore(today)) {
                                log.warn("QLV cannot change startDate to past date: {}", request.getStartDate());
                                throw new AppException(ErrorCode.FORBIDDEN);
                        }
                        // Chỉ cho QLV sửa trong vòng 1 giờ kể từ khi tạo
                        if (assignment.getCreatedAt() != null) {
                                Duration age = Duration.between(assignment.getCreatedAt(), LocalDateTime.now());
                                if (age.toMinutes() > 60) {
                                        log.warn("QLV cannot update assignment after 1 hour since creation: assignmentId={}, ageMinutes={}",
                                                        assignment.getId(), age.toMinutes());
                                        throw new AppException(ErrorCode.QLV_ACTION_WINDOW_EXPIRED);
                                }
                        }
                }

                // Business rule for SUPPORT: Only "Quản lý tổng" (QLT1) can update SUPPORT
                // assignments
                if (assignment.getAssignmentType() == com.company.company_clean_hub_be.entity.AssignmentType.SUPPORT) {
                        String roleCode = (updater.getRole() != null) ? updater.getRole().getCode() : "";
                        if (!"QLT1".equalsIgnoreCase(roleCode)) {
                                log.warn("User '{}' with role '{}' attempted to update SUPPORT assignment - forbidden",
                                                username, roleCode);
                                throw new AppException(ErrorCode.FORBIDDEN);
                        }
                }

                Employee employee = employeeRepository.findById(request.getEmployeeId())
                                .orElseThrow(() -> {
                                        log.error("[ASSIGNMENT][UPDATE] Employee not found, employeeId={}",
                                                        request.getEmployeeId());
                                        return new AppException(ErrorCode.EMPLOYEE_NOT_FOUND);
                                });

                // log.debug("[ASSIGNMENT][UPDATE] Mapping employeeId={}, contractId={} to
                // assignment id={}",
                // employee.getId(), contract.getId(), assignment.getId());
                //
                // if (request.getStartDate().isBefore(contract.getStartDate())) {
                // log.warn("[ASSIGNMENT][UPDATE] Invalid startDate={}, contractStartDate={}",
                // request.getStartDate(), contract.getStartDate());
                // throw new AppException(ErrorCode.ASSIGNMENT_START_DATE_BEFORE_CONTRACT);
                // }

                // Validate active assignment uniqueness (Chặn trùng cho cả IN_PROGRESS và
                // SCHEDULED)
                if (AssignmentStatus.IN_PROGRESS.equals(request.getStatus())
                                || AssignmentStatus.SCHEDULED.equals(request.getStatus())) {
                        List<Assignment> existingAssignments = assignmentRepository
                                        .findActiveAssignmentByEmployeeAndContractAndIdNot(
                                                        request.getEmployeeId(),
                                                        request.getContractId(),
                                                        id);

                        if (!existingAssignments.isEmpty()) {
                                log.warn("[ASSIGNMENT][UPDATE] Duplicate active assignment detected, employeeId={}, contractId={}",
                                                request.getEmployeeId(), request.getContractId());
                                throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS);
                        }
                }

                // R1/R2: validate Salary Note của hợp đồng trước khi lưu (chặn sai loại / lương ngoài khoảng)
                if (assignment.getContract() != null) {
                        salaryNoteValidator.validateAssignmentType(assignment.getContract(), assignment.getAssignmentType());
                        salaryNoteValidator.validateSalaryRange(assignment.getContract(),
                                assignment.getAssignmentType(), request.getSalaryAtTime());
                }

                // Update fields
                assignment.setEmployee(employee);
                assignment.setStartDate(request.getStartDate());
                assignment.setEndDate(calculateEndDate(request, assignment.getAssignmentType()));
                assignment.setStatus(request.getStatus());
                assignment.setSalaryAtTime(request.getSalaryAtTime());

                assignment.setAdditionalAllowance(request.getAdditionalAllowance());
                assignment.setMonthlySupport(request.getMonthlySupport());
                assignment.setAdvanceNote(request.getAdvanceNote());
                assignment.setDescription(request.getDescription());
                assignment.setUpdatedAt(LocalDateTime.now());

                Assignment updatedAssignment = assignmentRepository.save(assignment);

                log.info("[ASSIGNMENT][UPDATE] Assignment updated successfully, id={}", updatedAssignment.getId());

                // Recalculate work days
                YearMonth ym = YearMonth.from(request.getStartDate());
                LocalDate monthStart = ym.atDay(1);
                LocalDate monthEnd = ym.atEndOfMonth();

                int totalWorkDays = attendanceRepository
                                .findByAssignmentAndDateBetween(updatedAssignment.getId(), monthStart, monthEnd)
                                .size();

                updatedAssignment.setWorkDays(totalWorkDays);

                // Note: plannedDays is not updated via this endpoint.

                assignmentRepository.save(updatedAssignment);

                log.debug("[ASSIGNMENT][UPDATE] Recalculated workDays={}, assignmentId={}",
                                totalWorkDays, updatedAssignment.getId());

                log.info("[ASSIGNMENT][UPDATE] Finish update assignment, id={}", updatedAssignment.getId());

                // Task 19: Kiểm tra và gửi notification (không chặn flow chính)
                Contract updatedContract = updatedAssignment.getContract();
                if (updatedContract != null) {
                        try {
                                checkAndNotifyInsufficientStaff(updatedContract);
                        } catch (Exception e) {
                                log.warn("checkAndNotifyInsufficientStaff failed for contractId={}: {}",
                                                updatedContract.getId(), e.getMessage());
                        }
                        try {
                                checkAndNotifyAssignmentOverBudget(updatedAssignment);
                        } catch (Exception e) {
                                log.warn("checkAndNotifyAssignmentOverBudget failed for assignmentId={}: {}",
                                                updatedAssignment.getId(), e.getMessage());
                        }
                }

                return mapToResponse(updatedAssignment);
        }

        @Override
        public AssignmentResponse updateAllowanceAssignment(Long id, BigDecimal allowance) {
                log.info("[ASSIGNMENT][UPDATE] Start update assignment, id={}, allowance={}", id, allowance);

                if (id == null) {
                        log.error("[ASSIGNMENT][UPDATE] Assignment id is null");
                        throw new IllegalArgumentException("Assignment id must not be null");
                }

                Assignment assignment = assignmentRepository.findById(id)
                                .orElseThrow(() -> {
                                        log.error("[ASSIGNMENT][UPDATE] Assignment not found, id={}", id);
                                        return new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND);
                                });
                assignment.setAdditionalAllowance(allowance);
                Assignment updatedAssignment = assignmentRepository.save(assignment);

                log.info("[ASSIGNMENT][UPDATE] Assignment updated successfully, id={}", updatedAssignment.getId());

                return mapToResponse(updatedAssignment);
        }

        @Override
        @Transactional
        public AssignmentResponse updateAdvanceNote(Long id, BigDecimal advanceNote) {
                log.info("[ASSIGNMENT][ADVANCE] Updating advanceNote for assignment id={}", id);

                if (id == null) {
                        throw new IllegalArgumentException("Assignment id must not be null");
                }

                Assignment assignment = assignmentRepository.findById(id)
                        .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

                // DEBUG: log assignment info
                log.info("[ASSIGNMENT][ADVANCE] DEBUG assignment employeeId={}, assignmentId={}",
                        assignment.getEmployee() != null ? assignment.getEmployee().getId() : "NULL", id);

                // Only QLV can update advance note (bypass 1-hour lock)
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                log.info("[ASSIGNMENT][ADVANCE] DEBUG username from auth={}", username);

                User updater = userRepository.findByUsername(username)
                        .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));

                log.info("[ASSIGNMENT][ADVANCE] DEBUG updater class={}, updaterId={}, roleCode={}, isEmployee={}",
                        updater.getClass().getSimpleName(), updater.getId(),
                        updater.getRole() != null ? updater.getRole().getCode() : "NULL",
                        updater instanceof Employee);

                boolean isEmpInRepo = employeeRepository.findByUsername(username).isPresent();
                log.info("[ASSIGNMENT][ADVANCE] DEBUG employeeRepository.findByUsername({}) present={}",
                        username, isEmpInRepo);

                if (updater.getRole() != null && "QLV".equalsIgnoreCase(updater.getRole().getCode())) {
                        // QLV: only allow updating advanceNote, no time lock
                        log.info("[ASSIGNMENT][ADVANCE] QLV updating advanceNote: assignmentId={}, oldValue={}, newValue={}",
                                id, assignment.getAdvanceNote(), advanceNote);
                } else if (updater.getRole() != null && ("QLT1".equalsIgnoreCase(updater.getRole().getCode())
                        || "QLT2".equalsIgnoreCase(updater.getRole().getCode()))) {
                        // QLT1/QLT2: also allowed, no time lock
                        log.info("[ASSIGNMENT][ADVANCE] QLT updating advanceNote: assignmentId={}, oldValue={}, newValue={}",
                                id, assignment.getAdvanceNote(), advanceNote);
                } else if (updater instanceof Employee || employeeRepository.findByUsername(username).isPresent()) {
                        // Employee: only allowed to update their own assignment's advanceNote
                        Employee emp = employeeRepository.findByUsername(username)
                                .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));
                        if (assignment.getEmployee() == null || !emp.getId().equals(assignment.getEmployee().getId())) {
                                log.warn("[ASSIGNMENT][ADVANCE] Employee '{}' attempting to update advanceNote of another employee's assignment",
                                        username);
                                throw new AppException(ErrorCode.FORBIDDEN);
                        }
                        log.info("[ASSIGNMENT][ADVANCE] Employee updating own advanceNote: assignmentId={}, oldValue={}, newValue={}",
                                id, assignment.getAdvanceNote(), advanceNote);
                } else {
                        log.warn("[ASSIGNMENT][ADVANCE] Unauthorized user '{}' attempting to update advanceNote",
                                username);
                        throw new AppException(ErrorCode.FORBIDDEN);
                }

                assignment.setAdvanceNote(advanceNote);
                assignment.setUpdatedAt(LocalDateTime.now());
                Assignment updated = assignmentRepository.save(assignment);
                log.info("[ASSIGNMENT][ADVANCE] AdvanceNote updated successfully for assignment id={}", id);
                return mapToResponse(updated);
        }

        @Override
        @Transactional
        public void deleteAssignment(Long id) {
                Assignment assignment = assignmentRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User currentUser = userRepository.findByUsername(username).orElse(null);
                log.info("deleteAssignment by {}: assignmentId={}", username, id);

                // Nếu user là Quản lý vùng (QLV) thì chỉ được xóa assignment bắt đầu từ hôm nay
                // trở đi
                if (currentUser != null && currentUser.getRole() != null
                                && "QLV".equalsIgnoreCase(currentUser.getRole().getCode())) {
                        java.time.LocalDate today = java.time.LocalDate.now();
                        if (assignment.getStartDate() != null && assignment.getStartDate().isBefore(today)) {
                                log.warn("QLV cannot delete assignment that starts before today: assignmentId={}, startDate={}",
                                                id, assignment.getStartDate());
                                throw new AppException(ErrorCode.FORBIDDEN);
                        }

                        // Chỉ cho QLV xóa trong vòng 1 giờ sau khi tạo
                        if (assignment.getCreatedAt() != null) {
                                Duration age = Duration.between(assignment.getCreatedAt(), LocalDateTime.now());
                                if (age.toMinutes() > 60) {
                                        log.warn("QLV cannot delete assignment after 1 hour since creation: assignmentId={}, ageMinutes={}",
                                                        assignment.getId(), age.toMinutes());
                                        throw new AppException(ErrorCode.QLV_ACTION_WINDOW_EXPIRED);
                                }
                        }
                }

                // Business rule for SUPPORT: Only "Quản lý tổng" (QLT1) can delete SUPPORT
                // assignments
                if (assignment.getAssignmentType() == com.company.company_clean_hub_be.entity.AssignmentType.SUPPORT) {
                        String roleCode = (currentUser != null && currentUser.getRole() != null)
                                        ? currentUser.getRole().getCode()
                                        : "";
                        if (!"QLT1".equalsIgnoreCase(roleCode)) {
                                log.warn("User '{}' with role '{}' attempted to delete SUPPORT assignment - forbidden",
                                                username, roleCode);
                                throw new AppException(ErrorCode.FORBIDDEN);
                        }
                }

                // 0) Lấy thông tin employee và các tháng/năm có attendances trước khi xóa
                // (để kiểm tra và xóa payroll sau này)
                Long employeeId = assignment.getEmployee() != null ? assignment.getEmployee().getId() : null;
                List<Attendance> attendancesBeforeDelete = new ArrayList<>();
                if (employeeId != null) {
                        try {
                                attendancesBeforeDelete = attendanceRepository.findByAssignmentId(assignment.getId());
                                log.debug("Found {} attendances for assignmentId={} before deletion",
                                                attendancesBeforeDelete != null ? attendancesBeforeDelete.size() : 0,
                                                assignment.getId());
                        } catch (Exception ex) {
                                log.warn("Failed to get attendances before deletion for assignmentId={}: {}",
                                                assignment.getId(), ex.getMessage());
                        }
                }

                // 1) Delete related ratings and all attendances for the assignment before
                // removing the assignment
                try {
                        try {
                                ratingRepository.deleteByAssignmentId(assignment.getId());
                        } catch (Exception ignored) {
                        }

                        // Xóa work_schedules liên quan
                        try {
                                workScheduleRepository.deleteByAssignmentId(assignment.getId());
                                log.info("Deleted work_schedules for assignmentId={}", assignment.getId());
                        } catch (Exception ex) {
                                log.warn("Failed to delete work_schedules for assignmentId={}: {}", assignment.getId(), ex.getMessage());
                        }

                        // Xóa assignment_verification liên quan
                        try {
                                verificationRepository.findByAssignmentId(assignment.getId()).ifPresent(verification -> {
                                        // Xóa verification images trước
                                        try {
                                                List<com.company.company_clean_hub_be.entity.VerificationImage> images =
                                                        imageRepository.findByAssignmentVerificationId(verification.getId());
                                                if (images != null && !images.isEmpty()) {
                                                        imageRepository.deleteAll(images);
                                                        log.info("Deleted {} verification images for verificationId={}", images.size(), verification.getId());
                                                }
                                        } catch (Exception ex) {
                                                log.warn("Failed to delete verification images: {}", ex.getMessage());
                                        }
                                        verificationRepository.delete(verification);
                                        log.info("Deleted assignment_verification for assignmentId={}", assignment.getId());
                                });
                        } catch (Exception ex) {
                                log.warn("Failed to delete assignment_verification for assignmentId={}: {}", assignment.getId(), ex.getMessage());
                        }

                        // delete all attendances linked to this assignment via entity delete (avoids FK
                        // issues)
                        try {
                                List<Attendance> toDelete = attendanceRepository.findByAssignmentId(assignment.getId());
                                if (toDelete != null && !toDelete.isEmpty()) {
                                        attendanceRepository.deleteAll(toDelete);
                                        log.info("Deleted {} attendances for assignmentId={}", toDelete.size(),
                                                        assignment.getId());
                                }
                        } catch (Exception ex) {
                                log.warn("Failed to delete attendances for assignmentId={}: {}", assignment.getId(),
                                                ex.getMessage());
                        }
                } catch (Exception ex) {
                        log.warn("Failed to delete ratings/attendances for assignmentId={}: {}", assignment.getId(),
                                        ex.getMessage());
                }

                // 2) Delete assignment history entries that reference this assignment (old or
                // new)
                try {
                        List<com.company.company_clean_hub_be.entity.AssignmentHistory> oldHist = assignmentHistoryRepository
                                        .findByOldAssignmentId(assignment.getId());
                        List<com.company.company_clean_hub_be.entity.AssignmentHistory> newHist = assignmentHistoryRepository
                                        .findByNewAssignmentId(assignment.getId());

                        List<com.company.company_clean_hub_be.entity.AssignmentHistory> relatedHistories = new ArrayList<>();
                        if (oldHist != null && !oldHist.isEmpty())
                                relatedHistories.addAll(oldHist);
                        if (newHist != null && !newHist.isEmpty())
                                relatedHistories.addAll(newHist);

                        if (!relatedHistories.isEmpty()) {
                                log.info("Deleting {} assignment history records referencing assignmentId={}",
                                                relatedHistories.size(), assignment.getId());
                                assignmentHistoryRepository.deleteAll(relatedHistories);
                        }
                } catch (Exception ex) {
                        log.warn("Failed to delete assignment history for assignmentId={}: {}", assignment.getId(),
                                        ex.getMessage());
                }

                // 3) Delete the assignment itself
                assignmentRepository.delete(assignment);
                log.info("deleteAssignment completed: assignmentId={}", id);

                // Task 19: Kiểm tra thiếu nhân viên sau khi xóa assignment (không chặn flow chính)
                Contract deletedContract = assignment.getContract();
                if (deletedContract != null) {
                        try {
                                checkAndNotifyInsufficientStaff(deletedContract);
                        } catch (Exception e) {
                                log.warn("checkAndNotifyInsufficientStaff failed after delete for contractId={}: {}",
                                                deletedContract.getId(), e.getMessage());
                        }
                }

                // 4) Kiểm tra và xóa payroll nếu không còn assignment/attendance nào trong
                // tháng/năm đó
                if (employeeId != null && attendancesBeforeDelete != null && !attendancesBeforeDelete.isEmpty()) {
                        try {
                                // Lấy tất cả các tháng/năm duy nhất từ attendances đã xóa
                                Map<YearMonth, Boolean> monthYearMap = attendancesBeforeDelete.stream()
                                                .filter(att -> att.getDate() != null)
                                                .map(att -> YearMonth.from(att.getDate()))
                                                .distinct()
                                                .collect(Collectors.toMap(
                                                                ym -> ym,
                                                                ym -> false)); // false = chưa kiểm tra

                                log.debug("Checking payroll deletion for {} unique month/year combinations for employeeId={}",
                                                monthYearMap.size(), employeeId);

                                for (YearMonth yearMonth : monthYearMap.keySet()) {
                                        Integer month = yearMonth.getMonthValue();
                                        Integer year = yearMonth.getYear();

                                        try {
                                                // Kiểm tra xem còn assignment nào trong tháng/năm này không
                                                List<Assignment> remainingAssignments = assignmentRepository
                                                                .findDistinctAssignmentsByAttendanceMonthAndEmployee(
                                                                                month, year, employeeId);

                                                // Kiểm tra xem còn attendance nào trong tháng/năm này không
                                                List<Attendance> remainingAttendances = attendanceRepository
                                                                .findAttendancesByMonthYearAndEmployee(month, year,
                                                                                employeeId);

                                                boolean hasRemainingData = (remainingAssignments != null
                                                                && !remainingAssignments.isEmpty())
                                                                || (remainingAttendances != null
                                                                                && !remainingAttendances.isEmpty());

                                                log.debug("Month/Year {}/{} for employeeId={}: remainingAssignments={}, remainingAttendances={}, hasRemainingData={}",
                                                                month, year, employeeId,
                                                                remainingAssignments != null
                                                                                ? remainingAssignments.size()
                                                                                : 0,
                                                                remainingAttendances != null
                                                                                ? remainingAttendances.size()
                                                                                : 0,
                                                                hasRemainingData);

                                                // Nếu không còn assignment hoặc attendance nào, xóa payroll
                                                if (!hasRemainingData) {
                                                        Optional<com.company.company_clean_hub_be.entity.Payroll> payrollOpt = payrollRepository
                                                                        .findByEmployeeAndMonthAndYear(employeeId,
                                                                                        month,
                                                                                        year);

                                                        if (payrollOpt.isPresent()) {
                                                                com.company.company_clean_hub_be.entity.Payroll payroll = payrollOpt
                                                                                .get();
                                                                Long payrollId = payroll.getId();

                                                                // SAFETY CHECK: Không xóa payroll đã PAID (đã thanh toán hoàn toàn)
                                                                if (payroll.getStatus() == com.company.company_clean_hub_be.entity.PayrollStatus.PAID) {
                                                                        log.warn("Cannot delete PAID payroll payrollId={} for employeeId={}, month={}, year={} (orphan record but already paid)",
                                                                                        payrollId, employeeId, month, year);
                                                                } else {
                                                                        // Xóa payment history trước khi xóa payroll
                                                                        try {
                                                                                List<com.company.company_clean_hub_be.entity.PaymentHistory> paymentHistories = paymentHistoryRepository
                                                                                                .findByPayrollIdOrderByCreatedAtAsc(
                                                                                                                payrollId);
                                                                                if (paymentHistories != null
                                                                                                && !paymentHistories
                                                                                                                .isEmpty()) {
                                                                                        paymentHistoryRepository.deleteAll(
                                                                                                        paymentHistories);
                                                                                        log.info("Deleted {} payment history records for payrollId={}",
                                                                                                        paymentHistories.size(),
                                                                                                        payrollId);
                                                                                }
                                                                        } catch (Exception ex) {
                                                                                log.warn("Failed to delete payment history for payrollId={}: {}",
                                                                                                payrollId, ex.getMessage());
                                                                        }

                                                                        payrollRepository.delete(payroll);
                                                                        log.info("Deleted orphan payroll payrollId={} (status={}) for employeeId={}, month={}, year={} (no remaining assignments/attendances)",
                                                                                        payrollId, payroll.getStatus(), employeeId, month, year);
                                                                }
                                                        } else {
                                                                log.debug("No payroll found for employeeId={}, month={}, year={}",
                                                                                employeeId, month, year);
                                                        }
                                                } else {
                                                        log.debug("Keeping payroll for employeeId={}, month={}, year={} (still has assignments/attendances)",
                                                                        employeeId, month, year);
                                                }
                                        } catch (Exception ex) {
                                                log.warn("Failed to check/delete payroll for employeeId={}, month={}, year={}: {}",
                                                                employeeId, month, year, ex.getMessage());
                                        }
                                }
                        } catch (Exception ex) {
                                log.warn("Failed to process payroll deletion check after deleting assignmentId={}: {}",
                                                id, ex.getMessage());
                        }
                } else {
                        log.debug("Skipping payroll deletion check: employeeId={}, attendancesCount={}",
                                        employeeId,
                                        attendancesBeforeDelete != null ? attendancesBeforeDelete.size() : 0);
                }
        }

        @Override
        @Transactional
        public TemporaryAssignmentResponse temporaryReassignment(TemporaryReassignmentRequest request) {

                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                log.info("temporaryReassignment requested by {}: replacedId={}, replacementId={}, datesCount={}",
                                username, request.getReplacedEmployeeId(), request.getReplacementEmployeeId(),
                                request.getDates() != null ? request.getDates().size() : 0);

                Employee replacementEmployee = employeeRepository.findById(request.getReplacementEmployeeId())
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
                log.debug("Người thay: {} (ID: {})", replacementEmployee.getName(), replacementEmployee.getId());

                Employee replacedEmployee = employeeRepository.findById(request.getReplacedEmployeeId())
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
                log.debug("Người bị thay: {} (ID: {})", replacedEmployee.getName(), replacedEmployee.getId());

                // Lấy thông tin user đang thực hiện
                User currentUser = userRepository.findByUsername(username).orElse(null);

                // Nếu người thực hiện là Quản lý vùng (code = 'QLV') thì chỉ được điều động
                // thay thế từ hôm nay trở về sau; nếu là hôm nay thì chỉ được trễ nhất 1 tiếng trước giờ làm quy định
                if (currentUser != null && currentUser.getRole() != null
                                && "QLV".equalsIgnoreCase(currentUser.getRole().getCode())) {
                        LocalDate today = LocalDate.now();
                        LocalTime now = LocalTime.now();
                        for (LocalDate date : request.getDates()) {
                                if (date.isBefore(today)) {
                                        log.warn("QLV cannot perform temporary reassignment for past date: {}", date);
                                        throw new AppException(ErrorCode.FORBIDDEN);
                                }
                                if (date.isEqual(today)) {
                                        Contract contractForDate = null;
                                        if (request.getReplacedAssignmentId() != null) {
                                                Assignment replacedAssignmentForContract = assignmentRepository
                                                                .findById(request.getReplacedAssignmentId()).orElse(null);
                                                if (replacedAssignmentForContract != null) {
                                                        contractForDate = replacedAssignmentForContract.getContract();
                                                }
                                        } else {
                                                List<Assignment> activeAssignmentsForDate = assignmentRepository
                                                                .findActiveAssignmentsByEmployee(request.getReplacedEmployeeId(), date);
                                                if (!activeAssignmentsForDate.isEmpty()) {
                                                        contractForDate = activeAssignmentsForDate.get(0).getContract();
                                                }
                                        }

                                        LocalTime workStartTime = (contractForDate != null) ? contractForDate.getWorkStartTime() : null;
                                        // Chỉ kiểm tra nếu tìm được giờ làm từ hợp đồng
                                        // Được phân công đến tối đa 1 tiếng SAU giờ bắt đầu làm
                                        // Ví dụ: giờ làm 17:00 → được phân đến trước 18:00
                                        if (workStartTime != null) {
                                                LocalTime cutoffTime = workStartTime.plusHours(1);
                                                if (now.isAfter(cutoffTime)) {
                                                        log.warn("QLV không được điều động tạm thời sau thời hạn cho phép: now={}, cutoffTime={}, workStartTime={}",
                                                                        now, cutoffTime, workStartTime);
                                                        String detail = String.format(
                                                                "QLV chỉ được phân công/điều động trong vòng 1 tiếng kể từ khi ca làm bắt đầu.%nCa làm: %s — Hạn chót phân công: %s — Thời điểm hiện tại: %s",
                                                                workStartTime.toString(), cutoffTime.toString(), now.withSecond(0).withNano(0).toString());
                                                        throw new AppException(ErrorCode.QLV_CREATE_AFTER_ALLOWED_TIME, detail);
                                                }
                                        }
                                }
                        }
                }

                List<AttendanceResponse> createdAttendances = new ArrayList<>();
                List<AttendanceResponse> deletedAttendances = new ArrayList<>();
                List<WorkScheduleResponse> createdWorkSchedules = new ArrayList<>();
                List<WorkScheduleResponse> deletedWorkSchedules = new ArrayList<>();

                // Để lưu vào history
                Assignment oldAssignment = null;

                // --- Tạo 1 Temporary Assignment duy nhất trước vòng lặp ---
                // Xác định contract từ replaced assignment của ngày đầu tiên
                Contract contractForTemporary = null;
                LocalDate firstDate = request.getDates().get(0);

                if (request.getReplacedAssignmentId() != null) {
                        Assignment replacedAssignmentForContract = assignmentRepository
                                        .findById(request.getReplacedAssignmentId()).orElse(null);
                        if (replacedAssignmentForContract != null) {
                                contractForTemporary = replacedAssignmentForContract.getContract();
                        }
                } else {
                        // Tìm active assignment của người bị thay cho ngày đầu tiên
                        List<Assignment> activeAssignmentsForFirst = assignmentRepository
                                        .findActiveAssignmentsByEmployee(request.getReplacedEmployeeId(), firstDate);
                        if (!activeAssignmentsForFirst.isEmpty()) {
                                contractForTemporary = activeAssignmentsForFirst.get(0).getContract();
                        }
                }

                // Xác định tempStatus dựa trên ngày đầu tiên
                LocalDate today = LocalDate.now();
                AssignmentStatus tempStatus = firstDate.isAfter(today) ? AssignmentStatus.SCHEDULED
                                : AssignmentStatus.IN_PROGRESS;

                Assignment temporaryAssignment = Assignment.builder()
                                .employee(replacementEmployee)
                                .contract(contractForTemporary)
                                .assignmentType(AssignmentType.TEMPORARY)
                                .workDays(request.getDates().size())
                                .plannedDays(request.getDates().size())
                                .salaryAtTime(request.getSalaryAtTime())
                                .startDate(firstDate)
                                .status(tempStatus)
                                .description(request.getDescription() != null
                                                ? request.getDescription()
                                                : "Điều động thay thế")
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                Assignment savedTemporaryAssignment = assignmentRepository.save(temporaryAssignment);
                log.info("Created single temporary assignment ID: {} for {} dates, startDate={}",
                                savedTemporaryAssignment.getId(), request.getDates().size(), firstDate);

                // --- Verification check 1 lần trước vòng lặp ---
                boolean contractRequiresVerification = contractForTemporary != null
                                && Boolean.TRUE.equals(contractForTemporary.getRequiresImageVerification());
                boolean replacementNeedsVerification = false;
                AssignmentVerification tempVerification = null;
                int maxAttempts = 0;
                int verificationDayCount = 0;

                // Luôn kiểm tra verification cho nhân viên thay, bất kể contract có bật requiresImageVerification hay không
                // Nhân viên mới luôn cần chụp ảnh xác minh dù hợp đồng không yêu cầu chụp ảnh thường xuyên
                try {
                        replacementNeedsVerification = verificationService.requiresVerification(savedTemporaryAssignment);
                        log.info("Verification check for replacement employee {}: requiresVerification={}",
                                        replacementEmployee.getId(), replacementNeedsVerification);
                        if (replacementNeedsVerification) {
                                tempVerification = verificationService.createVerificationRequirement(
                                                savedTemporaryAssignment, "NEW_EMPLOYEE");
                                maxAttempts = tempVerification.getMaxAttempts();
                                log.info("Created verification requirement for temporary assignment: assignmentId={}, verificationId={}, maxAttempts={}",
                                                savedTemporaryAssignment.getId(),
                                                tempVerification.getId(),
                                                maxAttempts);
                        }
                } catch (Exception e) {
                        log.error("Error checking verification for replacement employee {}: {}",
                                        replacementEmployee.getId(), e.getMessage(), e);
                        replacementNeedsVerification = false;
                        // Fallback: will create WorkSchedule with REASSIGNMENT reason (if contract requires) or Attendance directly
                }

                // Collect unique replaced assignment IDs for metrics update after loop
                Set<Long> replacedAssignmentIds = new HashSet<>();

                // Xử lý từng ngày điều động
                for (LocalDate date : request.getDates()) {
                        log.debug("--- Xử lý ngày: {} ---", date);

                        List<Attendance> foundDeletedAttendances = new ArrayList<>();
                        WorkSchedule foundWorkSchedule = null;

                        // Nếu có replacedAssignmentId, CHỈ tìm attendance trong assignment đó
                        if (request.getReplacedAssignmentId() != null) {
                                Optional<Attendance> attOpt = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                                                request.getReplacedAssignmentId(), request.getReplacedEmployeeId(),
                                                date);
                                if (attOpt.isPresent()) {
                                        foundDeletedAttendances.add(attOpt.get());
                                        log.debug("Tìm thấy attendance từ replacedAssignmentId: {}", request.getReplacedAssignmentId());
                                } else {
                                        // Check for WorkSchedule if no Attendance found
                                        Optional<WorkSchedule> wsOpt = workScheduleRepository.findByAssignmentIdAndScheduledDate(
                                                        request.getReplacedAssignmentId(), date);
                                        if (wsOpt.isPresent()) {
                                                foundWorkSchedule = wsOpt.get();
                                                log.debug("Tìm thấy WorkSchedule từ replacedAssignmentId: {}", request.getReplacedAssignmentId());
                                        } else {
                                                log.error("Không tìm thấy attendance hoặc WorkSchedule cho assignmentId: {} vào ngày {}", request.getReplacedAssignmentId(), date);
                                                throw new AppException(ErrorCode.REPLACED_EMPLOYEE_NO_ATTENDANCE);
                                        }
                                }
                        } else {
                                // Không có replacedAssignmentId: tìm theo active assignment
                                log.debug("Tìm attendance theo active assignment cho employeeId={} ngày={}", request.getReplacedEmployeeId(), date);
                                List<Assignment> activeAssignments = assignmentRepository
                                                .findActiveAssignmentsByEmployee(request.getReplacedEmployeeId(), date);

                                for (Assignment a : activeAssignments) {
                                        Optional<Attendance> attOpt = attendanceRepository
                                                        .findByAssignmentAndEmployeeAndDate(
                                                                        a.getId(), request.getReplacedEmployeeId(),
                                                                        date);
                                        if (attOpt.isPresent()) {
                                                foundDeletedAttendances.add(attOpt.get());
                                                break;
                                        }
                                }

                                if (foundDeletedAttendances.isEmpty()) {
                                        foundDeletedAttendances = attendanceRepository.findAllByEmployeeAndDate(
                                                        request.getReplacedEmployeeId(), date);
                                }

                                // If still no Attendance found, check for WorkSchedule
                                if (foundDeletedAttendances.isEmpty()) {
                                        log.debug("Không tìm thấy attendance, kiểm tra WorkSchedule cho employeeId={} ngày={}", request.getReplacedEmployeeId(), date);
                                        List<WorkSchedule> workSchedules = workScheduleRepository.findByEmployeeIdAndDateRange(
                                                        request.getReplacedEmployeeId(), date, date);
                                        if (!workSchedules.isEmpty()) {
                                                foundWorkSchedule = workSchedules.get(0);
                                                log.debug("Tìm thấy WorkSchedule cho employeeId={} vào ngày {}", request.getReplacedEmployeeId(), date);
                                        }
                                }
                        }

                        log.debug("Attendance(s) của người bị thay (ID {}) vào ngày {}: {}",
                                        request.getReplacedEmployeeId(), date,
                                        foundDeletedAttendances.isEmpty() ? "KHÔNG CÓ" : "CÓ(" + foundDeletedAttendances.size() + ")");
                        log.debug("WorkSchedule của người bị thay: {}", foundWorkSchedule != null ? "CÓ" : "KHÔNG CÓ");

                        // Task 3.2: Check if employee has already worked (has Attendance)
                        // If Attendance exists, it means the employee has already worked (either auto-generated or photo-captured)
                        // For contracts with image verification, Attendance is only created after photo capture
                        // So if Attendance exists for such contracts, the employee has definitely worked
                        if (!foundDeletedAttendances.isEmpty()) {
                                // Check if this is for a contract with image verification
                                Attendance attendance = foundDeletedAttendances.get(0);
                                Assignment assignment = attendance.getAssignment();
                                if (assignment != null && assignment.getContract() != null 
                                                && assignment.getContract().getRequiresImageVerification() != null
                                                && assignment.getContract().getRequiresImageVerification()) {
                                        // For contracts with image verification, Attendance means photo was captured
                                        log.warn("Nhân viên đã chụp ảnh check-in, không thể thay thế: employeeId={}, date={}", request.getReplacedEmployeeId(), date);
                                        throw new AppException(ErrorCode.EMPLOYEE_ALREADY_WORKED);
                                }
                        }

                        // Task 3.3 & 3.4: Handle WorkSchedule-based reassignment
                        if (foundWorkSchedule != null && foundDeletedAttendances.isEmpty()) {
                                log.debug("Xử lý điều động dựa trên WorkSchedule cho ngày {}", date);
                                
                                Assignment replacedAssignmentEntity = foundWorkSchedule.getAssignment();
                                
                                // Lưu lại old assignment cho history (lần đầu tiên)
                                if (oldAssignment == null) {
                                        oldAssignment = replacedAssignmentEntity;
                                }

                                // Collect replaced assignment ID for metrics update after loop
                                replacedAssignmentIds.add(replacedAssignmentEntity.getId());

                                // Task 3.5: Save deleted WorkSchedule to response
                                WorkScheduleResponse deletedWsResponse = mapWorkScheduleToResponse(foundWorkSchedule);
                                deletedWorkSchedules.add(deletedWsResponse);

                                // Delete old WorkSchedule
                                workScheduleRepository.delete(foundWorkSchedule);
                                log.info("Deleted old WorkSchedule id={} for replacedEmployeeId={} on date={}",
                                                foundWorkSchedule.getId(), request.getReplacedEmployeeId(), date);

                                // If the deleted WorkSchedule was NEW_EMPLOYEE_VERIFICATION, create a replacement
                                // verification day so the new employee still completes 5 days of verification
                                if (foundWorkSchedule.getReason() == com.company.company_clean_hub_be.entity.WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION
                                                && foundWorkSchedule.getAssignmentVerification() != null) {
                                        // Find the last verification WorkSchedule date for this employee's assignment
                                        List<WorkSchedule> verificationSchedules = workScheduleRepository
                                                        .findByAssignmentIdAndReason(replacedAssignmentEntity.getId(),
                                                                        com.company.company_clean_hub_be.entity.WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION);
                                        LocalDate lastVerificationDate = verificationSchedules.stream()
                                                        .map(WorkSchedule::getScheduledDate)
                                                        .max(LocalDate::compareTo)
                                                        .orElse(date);

                                        // Find the next working day after the last verification date
                                        List<java.time.DayOfWeek> workingDays = replacedAssignmentEntity.getWorkingDaysPerWeek();
                                        if (workingDays == null || workingDays.isEmpty()) {
                                                workingDays = java.util.Arrays.asList(
                                                        java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.TUESDAY,
                                                        java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.THURSDAY,
                                                        java.time.DayOfWeek.FRIDAY, java.time.DayOfWeek.SATURDAY);
                                        }
                                        LocalDate nextDay = lastVerificationDate.plusDays(1);
                                        while (!workingDays.contains(nextDay.getDayOfWeek())) {
                                                nextDay = nextDay.plusDays(1);
                                        }

                                        // Only create if not already exists
                                        if (!workScheduleRepository.existsByAssignmentIdAndScheduledDate(
                                                        replacedAssignmentEntity.getId(), nextDay)) {
                                                WorkSchedule compensationSchedule = WorkSchedule.builder()
                                                                .assignment(replacedAssignmentEntity)
                                                                .employee(replacedEmployee)
                                                                .scheduledDate(nextDay)
                                                                .status(com.company.company_clean_hub_be.entity.WorkScheduleStatus.SCHEDULED)
                                                                .reason(com.company.company_clean_hub_be.entity.WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION)
                                                                .assignmentVerification(foundWorkSchedule.getAssignmentVerification())
                                                                .build();
                                                workScheduleRepository.save(compensationSchedule);
                                                log.info("Created compensation NEW_EMPLOYEE_VERIFICATION WorkSchedule for employeeId={} on date={} (replacing reassigned date={})",
                                                                replacedEmployee.getId(), nextDay, date);
                                        }
                                }

                                // Dùng chung logic với Attendance-based path (shared pre-loop variables)
                                // Ưu tiên 1: Nhân viên mới cần xác minh → luôn tạo WorkSchedule NEW_EMPLOYEE_VERIFICATION
                                // Ưu tiên 2: Contract yêu cầu chụp ảnh → tạo WorkSchedule REASSIGNMENT
                                // Ưu tiên 3: Còn lại → tạo Attendance trực tiếp
                                if (replacementNeedsVerification && verificationDayCount < maxAttempts) {
                                        // Nhân viên mới cần xác minh → tạo WorkSchedule NEW_EMPLOYEE_VERIFICATION
                                        WorkSchedule newWorkSchedule = WorkSchedule.builder()
                                                        .assignment(savedTemporaryAssignment)
                                                        .employee(replacementEmployee)
                                                        .scheduledDate(date)
                                                        .status(com.company.company_clean_hub_be.entity.WorkScheduleStatus.SCHEDULED)
                                                        .reason(WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION)
                                                        .assignmentVerification(tempVerification)
                                                        .createdAt(LocalDateTime.now())
                                                        .updatedAt(LocalDateTime.now())
                                                        .build();

                                        WorkSchedule savedWorkSchedule = workScheduleRepository.save(newWorkSchedule);
                                        verificationDayCount++;
                                        log.info("Created new WorkSchedule id={} for replacementEmployeeId={} on date={} reason=NEW_EMPLOYEE_VERIFICATION (WorkSchedule-based path, new employee verification)",
                                                        savedWorkSchedule.getId(), request.getReplacementEmployeeId(), date);

                                        WorkScheduleResponse createdWsResponse = mapWorkScheduleToResponse(savedWorkSchedule);
                                        createdWorkSchedules.add(createdWsResponse);
                                } else if (contractRequiresVerification) {
                                        // Contract yêu cầu chụp ảnh, nhân viên không mới hoặc hết verification days → WorkSchedule REASSIGNMENT
                                        WorkSchedule newWorkSchedule = WorkSchedule.builder()
                                                        .assignment(savedTemporaryAssignment)
                                                        .employee(replacementEmployee)
                                                        .scheduledDate(date)
                                                        .status(com.company.company_clean_hub_be.entity.WorkScheduleStatus.SCHEDULED)
                                                        .reason(WorkScheduleReason.REASSIGNMENT)
                                                        .assignmentVerification(null)
                                                        .createdAt(LocalDateTime.now())
                                                        .updatedAt(LocalDateTime.now())
                                                        .build();

                                        WorkSchedule savedWorkSchedule = workScheduleRepository.save(newWorkSchedule);
                                        log.info("Created new WorkSchedule id={} for replacementEmployeeId={} on date={} reason=REASSIGNMENT (WorkSchedule-based path, contract requires verification)",
                                                        savedWorkSchedule.getId(), request.getReplacementEmployeeId(), date);

                                        WorkScheduleResponse createdWsResponse = mapWorkScheduleToResponse(savedWorkSchedule);
                                        createdWorkSchedules.add(createdWsResponse);
                                } else {
                                        // Contract không yêu cầu chụp ảnh, nhân viên không mới → tạo Attendance trực tiếp
                                        Attendance newAttendance = Attendance.builder()
                                                        .employee(replacementEmployee)
                                                        .assignment(savedTemporaryAssignment)
                                                        .date(date)
                                                        .workHours(java.math.BigDecimal.valueOf(8))
                                                        .bonus(java.math.BigDecimal.ZERO)
                                                        .penalty(java.math.BigDecimal.ZERO)
                                                        .supportCost(java.math.BigDecimal.ZERO)
                                                        .deleted(false)
                                                        .isOvertime(false)
                                                        .overtimeAmount(java.math.BigDecimal.ZERO)
                                                        .description(request.getDescription() != null
                                                                        ? request.getDescription()
                                                                        : "Điều động thay thế ngày " + date)
                                                        .createdAt(LocalDateTime.now())
                                                        .updatedAt(LocalDateTime.now())
                                                        .build();

                                        Attendance savedAttendance = attendanceRepository.save(newAttendance);
                                        log.info("Created new Attendance id={} for replacementEmployeeId={} on date={} (WorkSchedule-based path, contract does not require verification, non-new employee)",
                                                        savedAttendance.getId(), request.getReplacementEmployeeId(), date);

                                        AttendanceResponse createdAttendanceResponse = mapAttendanceToResponse(savedAttendance);
                                        createdAttendances.add(createdAttendanceResponse);
                                }

                                continue; // Skip to next date
                        }

                        // Task 3.6: Only throw error if neither Attendance nor WorkSchedule found
                        if (foundDeletedAttendances.isEmpty() && foundWorkSchedule == null) {
                                log.error("Người bị thay không có attendance hoặc WorkSchedule vào ngày {}: employeeId={}", date, request.getReplacedEmployeeId());
                                throw new AppException(ErrorCode.REPLACED_EMPLOYEE_NO_ATTENDANCE);
                        }

                        Attendance deletedAttendance = null;
                        if (foundDeletedAttendances.size() == 1) {
                                deletedAttendance = foundDeletedAttendances.get(0);
                        } else {
                                final List<Attendance> candidates = foundDeletedAttendances;
                                deletedAttendance = candidates.stream()
                                                .filter(a -> a.getAssignment() != null && a.getAssignment()
                                                                .getStatus() == AssignmentStatus.IN_PROGRESS
                                                                && a.getAssignment()
                                                                                .getAssignmentType() != AssignmentType.TEMPORARY)
                                                .findFirst()
                                                .orElseGet(() -> candidates.stream()
                                                                .filter(a -> a.getAssignment() != null && a
                                                                                .getAssignment()
                                                                                .getStatus() == AssignmentStatus.IN_PROGRESS)
                                                                .findFirst()
                                                                .orElseGet(() -> candidates.stream()
                                                                                .filter(a -> a.getAssignment() != null
                                                                                                && a.getAssignment()
                                                                                                                .getStartDate() != null)
                                                                                .sorted((a1, a2) -> a2.getAssignment()
                                                                                                .getStartDate()
                                                                                                .compareTo(a1.getAssignment()
                                                                                                                .getStartDate()))
                                                                                .findFirst()
                                                                                .orElse(candidates
                                                                                                .get(0))));
                        }
                        Assignment replacedAssignmentEntity = deletedAttendance.getAssignment();
                        log.debug("Attendance tìm thấy: ID={}, workHours={}, isOvertime={}",
                                        deletedAttendance.getId(), deletedAttendance.getWorkHours(), deletedAttendance.getIsOvertime());

                        // Lưu lại old assignment cho history (lần đầu tiên)
                        if (oldAssignment == null) {
                                oldAssignment = replacedAssignmentEntity;
                        }

                        // Collect replaced assignment ID for metrics update after loop
                        replacedAssignmentIds.add(replacedAssignmentEntity.getId());

                        // Xóa attendance cũ của người bị thay và tạo mới cho người thay
                        // Tạo attendance luôn cho cả SCHEDULED (cron sau chỉ chuyển status)

                        // Kiểm tra người thay đã có attendance cùng assignment vào ngày này không (nếu
                        // có thì xóa để thay thế)
                        List<Attendance> replacementExisting = attendanceRepository.findAllByEmployeeAndDate(
                                        request.getReplacementEmployeeId(), date);

                        log.debug("Kiểm tra người thay đã có attendance vào ngày {}: {}",
                                        date, replacementExisting.isEmpty() ? "CHƯA CÓ" : "CÓ(" + replacementExisting.size() + ")");

                        // Nếu có attendance của người thay trùng assignment của deletedAttendance thì
                        // xóa chúng
                        for (Attendance ex : replacementExisting) {
                                if (ex.getAssignment() != null && deletedAttendance.getAssignment() != null
                                                && ex.getAssignment().getId()
                                                                .equals(deletedAttendance.getAssignment().getId())) {
                                        log.debug("Người thay đã có attendance cùng assignment vào ngày này (ID: {}) - sẽ xóa để thay thế", ex.getId());
                                        attendanceRepository.delete(ex);
                                        log.debug("Đã xóa attendance cũ của người thay cùng assignment, attendanceId={}", ex.getId());
                                }
                        }

                        // Lưu attendance bị xóa
                        AttendanceResponse deletedAttendanceResponse = mapAttendanceToResponse(deletedAttendance);
                        deletedAttendances.add(deletedAttendanceResponse);

                        // Sync WorkSchedule liên kết trước khi xóa attendance
                        // (đánh dấu attendanceDeleted=true để metrics không đếm WorkSchedule này)
                        workScheduleRepository.findByAttendanceId(deletedAttendance.getId()).ifPresent(ws -> {
                                ws.setAttendance(null);
                                ws.setAttendanceDeleted(true);
                                ws.setSyncNote("Attendance deleted by temporary reassignment at " + LocalDateTime.now());
                                ws.setLastSyncedAt(LocalDateTime.now());
                                workScheduleRepository.save(ws);
                                log.info("Synced WorkSchedule id={} with attendance deletion (temporary reassignment), set attendanceDeleted=true",
                                                ws.getId());
                        });

                        attendanceRepository.delete(deletedAttendance);
                        log.info("Deleted old attendance id={} for replacedEmployeeId={} on date={}",
                                        deletedAttendance.getId(), request.getReplacedEmployeeId(), date);

                        // Tạo Attendance hoặc WorkSchedule cho người thay dựa trên verification status
                        // Ưu tiên 1: Nhân viên mới cần xác minh → luôn tạo WorkSchedule NEW_EMPLOYEE_VERIFICATION (bất kể contract)
                        // Ưu tiên 2: Contract yêu cầu chụp ảnh → tạo WorkSchedule REASSIGNMENT
                        // Ưu tiên 3: Còn lại → tạo Attendance trực tiếp
                        if (replacementNeedsVerification && verificationDayCount < maxAttempts) {
                                // Nhân viên mới cần xác minh → tạo WorkSchedule NEW_EMPLOYEE_VERIFICATION
                                WorkSchedule newWorkSchedule = WorkSchedule.builder()
                                                .assignment(savedTemporaryAssignment)
                                                .employee(replacementEmployee)
                                                .scheduledDate(date)
                                                .status(com.company.company_clean_hub_be.entity.WorkScheduleStatus.SCHEDULED)
                                                .reason(WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION)
                                                .assignmentVerification(tempVerification)
                                                .createdAt(LocalDateTime.now())
                                                .updatedAt(LocalDateTime.now())
                                                .build();

                                WorkSchedule savedWorkSchedule = workScheduleRepository.save(newWorkSchedule);
                                verificationDayCount++;
                                log.info("Created new WorkSchedule id={} for replacementEmployeeId={} on date={} reason=NEW_EMPLOYEE_VERIFICATION (Attendance-based path, new employee verification)",
                                                savedWorkSchedule.getId(), request.getReplacementEmployeeId(), date);

                                WorkScheduleResponse createdWsResponse = mapWorkScheduleToResponse(savedWorkSchedule);
                                createdWorkSchedules.add(createdWsResponse);
                        } else if (contractRequiresVerification) {
                                // Contract yêu cầu chụp ảnh, nhân viên không mới hoặc hết verification days → WorkSchedule REASSIGNMENT
                                WorkScheduleReason wsReason = (replacementNeedsVerification && verificationDayCount < maxAttempts)
                                                ? WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION
                                                : WorkScheduleReason.REASSIGNMENT;

                                WorkSchedule newWorkSchedule = WorkSchedule.builder()
                                                .assignment(savedTemporaryAssignment)
                                                .employee(replacementEmployee)
                                                .scheduledDate(date)
                                                .status(com.company.company_clean_hub_be.entity.WorkScheduleStatus.SCHEDULED)
                                                .reason(wsReason)
                                                .assignmentVerification(null)
                                                .createdAt(LocalDateTime.now())
                                                .updatedAt(LocalDateTime.now())
                                                .build();

                                WorkSchedule savedWorkSchedule = workScheduleRepository.save(newWorkSchedule);
                                log.info("Created new WorkSchedule id={} for replacementEmployeeId={} on date={} reason={} (Attendance-based path, contract requires verification)",
                                                savedWorkSchedule.getId(), request.getReplacementEmployeeId(), date, wsReason);

                                WorkScheduleResponse createdWsResponse = mapWorkScheduleToResponse(savedWorkSchedule);
                                createdWorkSchedules.add(createdWsResponse);
                        } else {
                                // Contract không yêu cầu chụp ảnh, nhân viên không mới → tạo Attendance trực tiếp
                                Attendance newAttendance = Attendance.builder()
                                                .employee(replacementEmployee)
                                                .assignment(savedTemporaryAssignment)
                                                .date(date)
                                                .workHours(deletedAttendance.getWorkHours())
                                                .bonus(java.math.BigDecimal.ZERO)
                                                .penalty(java.math.BigDecimal.ZERO)
                                                .supportCost(java.math.BigDecimal.ZERO)
                                                .deleted(false)
                                                .isOvertime(deletedAttendance.getIsOvertime())
                                                .overtimeAmount(deletedAttendance.getOvertimeAmount())
                                                .description(request.getDescription() != null
                                                                ? request.getDescription()
                                                                : "Điều động thay thế " + replacedEmployee.getName() + " ngày "
                                                                                + date)
                                                .createdAt(LocalDateTime.now())
                                                .updatedAt(LocalDateTime.now())
                                                .build();

                                Attendance savedAttendance = attendanceRepository.save(newAttendance);
                                log.info("Created new attendance id={} for replacementEmployeeId={} on date={} (Attendance-based path, contract does not require verification, non-new employee)",
                                                savedAttendance.getId(), request.getReplacementEmployeeId(), date);

                                AttendanceResponse createdAttendanceResponse = mapAttendanceToResponse(savedAttendance);
                                createdAttendances.add(createdAttendanceResponse);
                        }
                }

                // --- Cập nhật metrics sau vòng lặp ---
                // Update metrics cho temporary assignment (người thay) 1 lần
                assignmentMetricsService.updateAssignmentMetrics(savedTemporaryAssignment.getId());
                log.info("Updated metrics for temporary assignment id={}", savedTemporaryAssignment.getId());

                // Update metrics cho tất cả replaced assignments (người bị thay) — xử lý mixed paths
                for (Long replacedId : replacedAssignmentIds) {
                        assignmentMetricsService.updateAssignmentMetrics(replacedId);
                        log.info("Updated metrics for replaced assignment id={}", replacedId);
                }

                // Lưu lịch sử điều động
                if (oldAssignment != null) {
                        Contract contract = oldAssignment.getContract();
                        AssignmentHistory history = AssignmentHistory.builder()
                                        .oldAssignment(oldAssignment)
                                        .newAssignment(savedTemporaryAssignment)
                                        .replacedEmployeeId(replacedEmployee.getId())
                                        .replacedEmployeeName(replacedEmployee.getName())
                                        .replacementEmployeeId(replacementEmployee.getId())
                                        .replacementEmployeeName(replacementEmployee.getName())
                                        .contractId(contract != null ? contract.getId() : null)
                                        .customerName(contract != null && contract.getCustomer() != null
                                                        ? contract.getCustomer().getName()
                                                        : null)
                                        .reassignmentDates(new ArrayList<>(request.getDates()))
                                        .reassignmentType(ReassignmentType.TEMPORARY)
                                        .notes(request.getDescription())
                                        .status(HistoryStatus.ACTIVE)
                                        .createdBy(currentUser)
                                        .build();
                        assignmentHistoryRepository.save(history);
                        log.info("Saved assignment history id={} by user={}", history.getId(), username);
                }

                // Tính công trong tháng (lấy tháng của ngày đầu tiên)
                if (!request.getDates().isEmpty()) {
                        YearMonth ym = YearMonth.from(firstDate);
                        LocalDate start = ym.atDay(1);
                        LocalDate end = ym.atEndOfMonth();

                        log.info("Calculating monthly totals for month={} ({} -> {})", ym, start, end);

                        int replacementTotal = attendanceRepository
                                        .findByEmployeeAndDateBetween(request.getReplacementEmployeeId(), start, end)
                                        .size();
                        log.debug("Tổng công người thay (ID {}): {}", request.getReplacementEmployeeId(), replacementTotal);

                        int replacedTotal = attendanceRepository
                                        .findByEmployeeAndDateBetween(request.getReplacedEmployeeId(), start, end)
                                        .size();
                        log.debug("Tổng công người bị thay (ID {}): {}", request.getReplacedEmployeeId(), replacedTotal);

                        log.info("temporaryReassignment result: created={}, deleted={}, createdWS={}, deletedWS={} (replacementTotal={}, replacedTotal={})",
                                        createdAttendances.size(), deletedAttendances.size(), 
                                        createdWorkSchedules.size(), deletedWorkSchedules.size(),
                                        replacementTotal, replacedTotal);

                        int totalProcessed = createdAttendances.size() + createdWorkSchedules.size();
                        
                        return TemporaryAssignmentResponse.builder()
                                        .createdAttendances(createdAttendances)
                                        .deletedAttendances(deletedAttendances)
                                        .createdWorkSchedules(createdWorkSchedules)
                                        .deletedWorkSchedules(deletedWorkSchedules)
                                        .replacementEmployeeTotalDays(replacementTotal)
                                        .replacedEmployeeTotalDays(replacedTotal)
                                        .processedDaysCount(totalProcessed)
                                        .message(String.format(
                                                        "Điều động thành công %d ngày: %s (+%d công, tổng: %d) thay %s (-%d công, tổng: %d)",
                                                        totalProcessed,
                                                        replacementEmployee.getName(),
                                                        totalProcessed,
                                                        replacementTotal,
                                                        replacedEmployee.getName(),
                                                        createdAttendances.size() + deletedWorkSchedules.size(),
                                                        replacedTotal))
                                        .build();
                }

                log.warn("temporaryReassignment: no dates processed for request by {}", username);
                return TemporaryAssignmentResponse.builder()
                                .createdAttendances(createdAttendances)
                                .deletedAttendances(deletedAttendances)
                                .createdWorkSchedules(createdWorkSchedules)
                                .deletedWorkSchedules(deletedWorkSchedules)
                                .replacementEmployeeTotalDays(0)
                                .replacedEmployeeTotalDays(0)
                                .processedDaysCount(0)
                                .message("Không có ngày nào được xử lý")
                                .build();
        }

        @Override
        public List<AssignmentResponse> getEmployeesByCustomer(Long customerId) {
                customerRepository.findById(customerId)
                                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

                List<Assignment> assignments = assignmentRepository.findActiveAssignmentsByCustomer(customerId);

                return assignments.stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public List<AssignmentResponse> getAllEmployeesByCustomer(Long customerId) {
                customerRepository.findById(customerId)
                                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

                List<Assignment> assignments = assignmentRepository.findAllAssignmentsByCustomer(customerId);

                return assignments.stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public PageResponse<AssignmentResponse> getAllEmployeesByCustomerWithFilters(
                        Long customerId,
                        ContractType contractType,
                        AssignmentStatus status,
                        Integer month,
                        Integer year,
                        int page,
                        int pageSize) {

                customerRepository.findById(customerId)
                                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));
                Pageable pageable = PageRequest.of(page - 1, pageSize, Sort.by("startDate").descending());
                Page<Assignment> assignmentPage = assignmentRepository.findAllAssignmentsByCustomerWithFilters(
                                customerId, contractType, status, month, year, pageable);

                List<AssignmentResponse> items = assignmentPage.getContent().stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
                return PageResponse.<AssignmentResponse>builder()
                                .content(items)
                                .page(assignmentPage.getNumber())
                                .pageSize(assignmentPage.getSize())
                                .totalElements(assignmentPage.getTotalElements())
                                .totalPages(assignmentPage.getTotalPages())
                                .first(assignmentPage.isFirst())
                                .last(assignmentPage.isLast())
                                .build();
        }

        @Override
        public PageResponse<com.company.company_clean_hub_be.dto.response.AssignmentsByContractResponse> getAssignmentsByCustomerGroupedByContract(
                        Long customerId,
                        String keyword,
                        Long contractId,
                        ContractType contractType,
                        AssignmentStatus status,
                        Integer month,
                        Integer year,
                        int page,
                        int pageSize) {

                customerRepository.findById(customerId)
                                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

                // Get all contracts for this customer
                List<Contract> allContracts = contractRepository.findByCustomerId(customerId);

                // Filter contracts by contractId if provided
                if (contractId != null) {
                        allContracts = allContracts.stream()
                                        .filter(c -> c.getId().equals(contractId))
                                        .collect(Collectors.toList());
                }

                // Filter contracts by contractType if provided
                if (contractType != null) {
                        allContracts = allContracts.stream()
                                        .filter(c -> c.getContractType() == contractType)
                                        .collect(Collectors.toList());
                }

                // Fetch all assignments with filters
                Pageable unpaged = Pageable.unpaged();
                Page<Assignment> allAssignments = assignmentRepository.findAllAssignmentsByCustomerWithFilters(
                                customerId, contractType, status, month, year, unpaged);

                // Filter assignments by keyword if provided (employee name or code)
                List<Assignment> filteredAssignments = allAssignments.getContent();
                if (keyword != null && !keyword.trim().isEmpty()) {
                        String lowerKeyword = keyword.toLowerCase().trim();
                        filteredAssignments = filteredAssignments.stream()
                                        .filter(a -> {
                                                if (a.getEmployee() == null)
                                                        return false;
                                                Employee emp = a.getEmployee();
                                                boolean matchName = emp.getName() != null &&
                                                                emp.getName().toLowerCase().contains(lowerKeyword);
                                                boolean matchCode = emp.getEmployeeCode() != null &&
                                                                emp.getEmployeeCode().toLowerCase()
                                                                                .contains(lowerKeyword);
                                                return matchName || matchCode;
                                        })
                                        .collect(Collectors.toList());
                }

                // Group assignments by contract
                Map<Long, List<Assignment>> assignmentsByContract = filteredAssignments.stream()
                                .filter(a -> a.getContract() != null)
                                .collect(Collectors.groupingBy(a -> a.getContract().getId()));

                // Pagination on contracts
                int totalContracts = allContracts.size();
                int safePage = Math.max(0, page);
                int safePageSize = Math.max(1, pageSize);
                int fromIndex = Math.min(totalContracts, safePage * safePageSize);
                int toIndex = Math.min(totalContracts, fromIndex + safePageSize);

                List<Contract> pagedContracts = allContracts.subList(fromIndex, toIndex);

                // Build response for each contract in the page (even if no assignments)
                List<com.company.company_clean_hub_be.dto.response.AssignmentsByContractResponse> result = new ArrayList<>();
                for (Contract contract : pagedContracts) {
                        List<Assignment> contractAssignments = assignmentsByContract.getOrDefault(contract.getId(),
                                        new ArrayList<>());
                        List<AssignmentResponse> rawResponses = contractAssignments.stream()
                                        .map(this::mapToResponse)
                                        .collect(Collectors.toList());

                        java.util.Map<String, AssignmentResponse> grouped = new java.util.LinkedHashMap<>();
                        for (AssignmentResponse res : rawResponses) {
                                String key = String.format("%d_%s_%s_%s",
                                                res.getEmployeeId(),
                                                res.getSalaryAtTime() != null ? res.getSalaryAtTime().toString()
                                                                : "null",
                                                res.getAssignmentType() != null ? res.getAssignmentType() : "null",
                                                res.getStatus() != null ? res.getStatus().name() : "null");

                                if (grouped.containsKey(key)) {
                                        AssignmentResponse existing = grouped.get(key);
                                        existing.setWorkDays((existing.getWorkDays() == null ? 0
                                                        : existing.getWorkDays())
                                                        + (res.getWorkDays() == null ? 0 : res.getWorkDays()));
                                        existing.setPlannedDays((existing.getPlannedDays() == null ? 0
                                                        : existing.getPlannedDays())
                                                        + (res.getPlannedDays() == null ? 0 : res.getPlannedDays()));
                                        if (res.getStartDate() != null) {
                                                if (existing.getStartDate() == null || res.getStartDate()
                                                                .isBefore(existing.getStartDate())) {
                                                        existing.setStartDate(res.getStartDate());
                                                }
                                        }
                                } else {
                                        if (res.getWorkDays() == null)
                                                res.setWorkDays(0);
                                        if (res.getPlannedDays() == null)
                                                res.setPlannedDays(0);
                                        grouped.put(key, res);
                                }
                        }
                        List<AssignmentResponse> assignmentResponses = new java.util.ArrayList<>(grouped.values());

                        result.add(new com.company.company_clean_hub_be.dto.response.AssignmentsByContractResponse(
                                        contract.getId(),
                                        contract.getDescription(),
                                        contract.getStartDate(),
                                        contract.getContractType(),
                                        assignmentResponses));
                }

                int totalPages = (int) Math.ceil((double) totalContracts / (double) safePageSize);
                boolean first = safePage == 0;
                boolean last = safePage >= totalPages - 1;

                return PageResponse.<com.company.company_clean_hub_be.dto.response.AssignmentsByContractResponse>builder()
                                .content(result)
                                .page(safePage)
                                .pageSize(safePageSize)
                                .totalElements(totalContracts)
                                .totalPages(totalPages)
                                .first(first)
                                .last(last)
                                .build();
        }

        @Override
        public List<com.company.company_clean_hub_be.dto.response.CustomerResponse> getCustomersByEmployee(
                        Long employeeId) {
                employeeRepository.findById(employeeId)
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

                List<com.company.company_clean_hub_be.entity.Customer> customers = assignmentRepository
                                .findActiveCustomersByEmployee(employeeId);

                return customers.stream()
                                .map(c -> com.company.company_clean_hub_be.dto.response.CustomerResponse.builder()
                                                .id(c.getId())
                                                .customerCode(c.getCustomerCode())
                                                .name(c.getName())
                                                .phone(c.getPhone())
                                                .email(c.getEmail())
                                                .address(c.getAddress())
                                                .company(c.getCompany())
                                                .status(c.getStatus())
                                                .createdAt(c.getCreatedAt())
                                                .updatedAt(c.getUpdatedAt())
                                                .description(c.getDescription())
                                                .contactInfo(c.getContactInfo())
                                                .taxCode(c.getTaxCode())
                                                .build())
                                .collect(Collectors.toList());
        }

        @Override
        public List<AssignmentResponse> getAssignmentsByEmployee(Long employeeId) {
                // validate employee exists
                employeeRepository.findById(employeeId)
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

                // Use existing repository method to find active assignments up to today
                List<Assignment> assignments = assignmentRepository.findByEmployeeId(employeeId);

                return assignments.stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public List<AssignmentResponse> getTodayAssignmentsForCapture(Long employeeId) {
                // validate employee exists
                employeeRepository.findById(employeeId)
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

                LocalDate today = LocalDate.now();

                // Tìm work_schedules của nhân viên hôm nay với status SCHEDULED
                // (chưa chụp ảnh, chưa bị MISSED, chưa bị CANCELLED)
                List<WorkSchedule> todaySchedules = workScheduleRepository
                                .findByScheduledDateAndStatus(today, WorkScheduleStatus.SCHEDULED)
                                .stream()
                                .filter(ws -> ws.getEmployee() != null
                                                && ws.getEmployee().getId().equals(employeeId))
                                .collect(Collectors.toList());

                log.info("[TODAY-CAPTURE] employeeId={}, today={}, SCHEDULED work_schedules found: {}", 
                    employeeId, today, todaySchedules.size());
                todaySchedules.forEach(ws -> log.info("[TODAY-CAPTURE]   ws={}, assignmentId={}, reason={}, status={}", 
                    ws.getId(), ws.getAssignment() != null ? ws.getAssignment().getId() : "NULL",
                    ws.getReason(), ws.getStatus()));

                List<AssignmentResponse> result = todaySchedules.stream()
                                .filter(ws -> {
                                        if (ws.getAssignment() == null) return false;
                                        AssignmentStatus status = ws.getAssignment().getStatus();
                                        return status == AssignmentStatus.IN_PROGRESS
                                                        || status == AssignmentStatus.SCHEDULED;
                                })
                                .map(ws -> mapToResponse(ws.getAssignment()))
                                // Loại trùng nếu 1 assignment có nhiều work_schedule trong ngày
                                .collect(Collectors.collectingAndThen(
                                                Collectors.toMap(
                                                                AssignmentResponse::getId,
                                                                r -> r,
                                                                (a, b) -> a),
                                                m -> new ArrayList<>(m.values())));

                log.info("[DEBUG] getTodayAssignmentsForCapture: Returning {} assignments", result.size());
                return result;
        }

        @Override
        public PageResponse<AssignmentResponse> getAssignmentsByContract(Long contractId,
                        com.company.company_clean_hub_be.entity.AssignmentStatus status, Integer month, Integer year,
                        int page, int pageSize) {
                contractRepository.findById(contractId)
                                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

                int safePage = Math.max(0, page <= 0 ? 0 : page - 1);
                int safePageSize = Math.max(1, pageSize);
                Pageable pageable = PageRequest.of(safePage, safePageSize, Sort.by("startDate").descending());

                Page<Assignment> assignmentPage = assignmentRepository.findByContractIdWithFilters(contractId, status,
                                month,
                                year, pageable);

                List<AssignmentResponse> items = assignmentPage.getContent().stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());

                return PageResponse.<AssignmentResponse>builder()
                                .content(items)
                                .page(assignmentPage.getNumber())
                                .pageSize(assignmentPage.getSize())
                                .totalElements(assignmentPage.getTotalElements())
                                .totalPages(assignmentPage.getTotalPages())
                                .first(assignmentPage.isFirst())
                                .last(assignmentPage.isLast())
                                .build();
        }

        @Override
        public PageResponse<AssignmentResponse> getAssignmentsByEmployeeWithFilters(Long employeeId, Long customerId,
                        Integer month, Integer year, int page, int pageSize) {
                // validate employee exists
                employeeRepository.findById(employeeId)
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

                Pageable pageable = PageRequest.of(page, pageSize, Sort.by("startDate").descending());
                Page<Assignment> assignmentPage = assignmentRepository.findAssignmentsByEmployeeWithFilters(
                                employeeId, customerId, month, year, pageable);
                log.info("2257: {}", assignmentPage);
                List<AssignmentResponse> items = assignmentPage.getContent().stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
                return PageResponse.<AssignmentResponse>builder()
                                .content(items)
                                .page(assignmentPage.getNumber())
                                .pageSize(assignmentPage.getSize())
                                .totalElements(assignmentPage.getTotalElements())
                                .totalPages(assignmentPage.getTotalPages())
                                .first(assignmentPage.isFirst())
                                .last(assignmentPage.isLast())
                                .build();
        }

        @Override
        public List<AssignmentResponse> getAssignmentsByEmployeeMonthYear(Long employeeId, Integer month,
                        Integer year) {
                // validate employee exists
                employeeRepository.findById(employeeId)
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
                List<Assignment> assignments = assignmentRepository.findAssignmentsByEmployeeAndMonthAndYear(employeeId,
                                month, year);

                return assignments.stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public PageResponse<com.company.company_clean_hub_be.dto.response.EmployeeResponse> getEmployeesNotAssignedToCustomer(
                        Long customerId, com.company.company_clean_hub_be.entity.EmploymentType employmentType,
                        Integer month, Integer year, int page, int pageSize) {
                customerRepository.findById(customerId)
                                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

                Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
                Page<Employee> employeePage;

                if (month != null && year != null) {
                        // Lọc theo tháng năm
                        employeePage = employeeRepository.findEmployeesNotAssignedToCustomerByMonth(
                                        customerId, employmentType, month, year, pageable);
                } else {
                        // Không lọc tháng năm (chỉ lấy chưa có assignment IN_PROGRESS)
                        employeePage = employeeRepository.findEmployeesNotAssignedToCustomer(customerId, employmentType,
                                        pageable);
                }

                List<com.company.company_clean_hub_be.dto.response.EmployeeResponse> items = employeePage.getContent()
                                .stream()
                                .map(this::mapEmployeeToResponse)
                                .collect(Collectors.toList());

                return PageResponse.<com.company.company_clean_hub_be.dto.response.EmployeeResponse>builder()
                                .content(items)
                                .page(employeePage.getNumber())
                                .pageSize(employeePage.getSize())
                                .totalElements(employeePage.getTotalElements())
                                .totalPages(employeePage.getTotalPages())
                                .first(employeePage.isFirst())
                                .last(employeePage.isLast())
                                .build();
        }

        @Override
        public PageResponse<AttendanceResponse> getAttendancesByAssignment(Long assignmentId, Integer month,
                        Integer year, int page, int pageSize) {
                Assignment assignment = assignmentRepository.findById(assignmentId)
                                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

                Pageable pageable = PageRequest.of(page, pageSize, Sort.by("date").descending());
                Page<Attendance> attendancePage = attendanceRepository.findByAssignmentAndFilters(assignmentId, month,
                                year, pageable);

                List<AttendanceResponse> items = attendancePage.getContent().stream()
                                .map(this::mapAttendanceToResponse)
                                .collect(Collectors.toList());

                return PageResponse.<AttendanceResponse>builder()
                                .content(items)
                                .page(attendancePage.getNumber())
                                .pageSize(attendancePage.getSize())
                                .totalElements(attendancePage.getTotalElements())
                                .totalPages(attendancePage.getTotalPages())
                                .first(attendancePage.isFirst())
                                .last(attendancePage.isLast())
                                .build();
        }

        private com.company.company_clean_hub_be.dto.response.EmployeeResponse mapEmployeeToResponse(
                        Employee employee) {
                return com.company.company_clean_hub_be.dto.response.EmployeeResponse.builder()
                                .id(employee.getId())
                                .username(employee.getUsername())
                                .phone(employee.getPhone())
                                .email(employee.getEmail())
                                .roleId(employee.getRole() != null ? employee.getRole().getId() : null)
                                .roleName(employee.getRole() != null ? employee.getRole().getName() : null)
                                .status(employee.getStatus())
                                .employeeCode(employee.getEmployeeCode())
                                .cccd(employee.getCccd())
                                .address(employee.getAddress())
                                .name(employee.getName())
                                .bankAccount(employee.getBankAccount())
                                .bankName(employee.getBankName())
                                .description(employee.getDescription())
                                .employmentType(employee.getEmploymentType())
                                .monthlySalary(employee.getMonthlySalary())
                                .allowance(employee.getAllowance())
                                .insuranceSalary(employee.getInsuranceSalary())
                                // [DEPRECATED] .monthlyAdvanceLimit(employee.getMonthlyAdvanceLimit())
                                .monthlySupport(employee.getMonthlySupport())
                                .cccdFrontImage(employee.getCccdFrontImage())
                                .cccdBackImage(employee.getCccdBackImage())
                                .createdAt(employee.getCreatedAt())
                                .updatedAt(employee.getUpdatedAt())
                                .build();
        }

        private AttendanceResponse mapAttendanceToResponse(Attendance attendance) {
                Assignment assignment = attendance.getAssignment();
                Employee employee = assignment != null ? assignment.getEmployee() : null;
                return AttendanceResponse.builder()
                                .id(attendance.getId())
                                .employeeId(employee != null ? employee.getId() : null)
                                .employeeName(employee != null ? employee.getName() : null)
                                .employeeCode(employee != null ? employee.getEmployeeCode() : null)
                                .assignmentId(assignment != null ? assignment.getId() : null)
                                .assignmentType(assignment != null && assignment.getAssignmentType() != null
                                                ? assignment.getAssignmentType().name()
                                                : null)
                                .customerId(assignment != null && assignment.getContract() != null
                                                && assignment.getContract().getCustomer() != null
                                                                ? assignment.getContract().getCustomer().getId()
                                                                : null)
                                .customerName(assignment != null && assignment.getContract() != null
                                                && assignment.getContract().getCustomer() != null
                                                                ? assignment.getContract().getCustomer().getName()
                                                                : null)
                                .date(attendance.getDate())
                                .workHours(attendance.getWorkHours())
                                .bonus(attendance.getBonus())
                                .penalty(attendance.getPenalty())
                                .supportCost(attendance.getSupportCost())
                                .isOvertime(attendance.getIsOvertime())
                                .overtimeAmount(attendance.getOvertimeAmount())
                                .description(attendance.getDescription())
                                .createdAt(attendance.getCreatedAt())
                                .updatedAt(attendance.getUpdatedAt())
                                .build();
        }

        private WorkScheduleResponse mapWorkScheduleToResponse(WorkSchedule workSchedule) {
                Assignment assignment = workSchedule.getAssignment();
                Employee employee = workSchedule.getEmployee();
                Contract contract = assignment != null ? assignment.getContract() : null;
                
                return WorkScheduleResponse.builder()
                                .id(workSchedule.getId())
                                .assignmentId(assignment != null ? assignment.getId() : null)
                                .employeeId(employee != null ? employee.getId() : null)
                                .employeeName(employee != null ? employee.getName() : null)
                                .contractId(contract != null ? contract.getId() : null)
                                .scheduledDate(workSchedule.getScheduledDate())
                                .status(workSchedule.getStatus())
                                .reason(workSchedule.getReason())
                                .assignmentVerificationId(workSchedule.getAssignmentVerification() != null 
                                                ? workSchedule.getAssignmentVerification().getId() : null)
                                .verificationImageId(workSchedule.getVerificationImage() != null 
                                                ? workSchedule.getVerificationImage().getId() : null)
                                .attendanceId(workSchedule.getAttendance() != null 
                                                ? workSchedule.getAttendance().getId() : null)
                                .photoCapturedAt(workSchedule.getPhotoCapturedAt())
                                .canCapturePhoto(workSchedule.canCapturePhoto())
                                .attendanceDeleted(workSchedule.getAttendanceDeleted())
                                .syncNote(workSchedule.getSyncNote())
                                .lastSyncedAt(workSchedule.getLastSyncedAt())
                                .createdAt(workSchedule.getCreatedAt())
                                .updatedAt(workSchedule.getUpdatedAt())
                                .build();
        }

        private AssignmentResponse mapToResponse(Assignment assignment) {
                Contract contract = assignment.getContract();
                return AssignmentResponse.builder()
                                .id(assignment.getId())
                                .employeeId(assignment.getEmployee().getId())
                                .employeeName(assignment.getEmployee().getName())
                                .employeeCode(assignment.getEmployee().getEmployeeCode())
                                .assignmentType(assignment.getAssignmentType().name())
                                .scope(assignment.getScope() != null ? assignment.getScope().name()
                                                : AssignmentScope.CONTRACT.name())
                                .customerId(contract != null && contract.getCustomer() != null
                                                ? contract.getCustomer().getId()
                                                : null)
                                .customerName(contract != null && contract.getCustomer() != null
                                                ? contract.getCustomer().getName()
                                                : null)
                                .customerCode(contract != null && contract.getCustomer() != null
                                                ? contract.getCustomer().getCustomerCode()
                                                : null)
                                .contractId(contract != null ? contract.getId() : null)
                                .contractDescription(contract != null ? contract.getDescription() : null)
                                .contractStartDate(contract != null ? contract.getStartDate() : null)
                                .contractEndDate(contract != null ? contract.getEndDate() : null)
                                .contractType(contract != null ? contract.getContractType() : null)
                                .startDate(assignment.getStartDate())
                                .endDate(assignment.getEndDate())
                                .status(assignment.getStatus())
                                .salaryAtTime(assignment.getSalaryAtTime())
                                .workDays(assignment.getWorkDays())
                                .plannedDays(assignment.getPlannedDays())
                                .workingDaysPerWeek(assignment.getWorkingDaysPerWeek())
                                .additionalAllowance(assignment.getAdditionalAllowance())
                                .monthlySupport(assignment.getMonthlySupport())
                                .advanceNote(assignment.getAdvanceNote())
                                .description(assignment.getDescription())
                                .createdAt(assignment.getCreatedAt())
                                .updatedAt(assignment.getUpdatedAt())
                                .assignedById(assignment.getAssignedBy() != null
                                                ? assignment.getAssignedBy().getId()
                                                : null)
                                .assignedByUsername(assignment.getAssignedBy() != null
                                                ? assignment.getAssignedBy().getUsername()
                                                : null)
                                .build();
        }

        // Helper method: Tìm ngày làm việc đầu tiên
        private LocalDate findFirstWorkingDay(LocalDate startDate, List<java.time.DayOfWeek> workingDays) {
                if (workingDays == null || workingDays.isEmpty()) {
                        return startDate;
                }

                LocalDate currentDate = startDate;
                LocalDate endDate = startDate.plusMonths(1);

                while (!currentDate.isAfter(endDate)) {
                        if (workingDays.contains(currentDate.getDayOfWeek())) {
                                return currentDate;
                        }
                        currentDate = currentDate.plusDays(1);
                }

                return startDate; // Fallback
        }

        /**
         * Tự động tạo chấm công cho assignment dựa vào workingDaysPerWeek
         * - Nếu hợp đồng ONE_TIME: chỉ tạo 1 attendance ngày đầu tiên
         * - Nếu hợp đồng khác: tạo từ startDate đến cuối tháng của startDate (hoặc cuối
         * tháng hiện tại nếu là tháng hiện tại)
         */
        private void autoGenerateAttendancesForAssignment(Assignment assignment, LocalDate startDate) {
                log.info("[DEBUG] ===== autoGenerateAttendancesForAssignment called =====");
                log.info("[DEBUG] Assignment ID: {}, Start Date: {}", assignment.getId(), startDate);

                // Reload assignment từ DB để tránh lazy loading issue với @ElementCollection workingDaysPerWeek
                Assignment freshAssignment = assignmentRepository.findById(assignment.getId()).orElse(assignment);

                log.info("[DEBUG] WorkingDaysPerWeek: {}, size: {}", 
                        freshAssignment.getWorkingDaysPerWeek(), 
                        freshAssignment.getWorkingDaysPerWeek() != null ? freshAssignment.getWorkingDaysPerWeek().size() : "NULL");

                if (freshAssignment.getWorkingDaysPerWeek() == null || freshAssignment.getWorkingDaysPerWeek().isEmpty()) {
                        log.info("[DEBUG] No working days defined, returning");
                        return;
                }

                // ===== CRITICAL: CHECK VERIFICATION FIRST =====
                boolean requiresVerification = verificationService.requiresVerification(assignment);
                log.info("[DEBUG] Assignment {} requires verification: {}", assignment.getId(), requiresVerification);
                log.info("[DEBUG] Assignment details: startDate={}, endDate={}, contractType={}", 
                        assignment.getStartDate(), assignment.getEndDate(), 
                        assignment.getContract() != null ? assignment.getContract().getContractType() : "NULL");

                if (requiresVerification) {
                        log.info("[DEBUG] Creating work_schedules instead of attendances for assignment {}", assignment.getId());
                        
                        // Determine reason
                        boolean isNewEmployee = verificationService.isEmployeeNew(assignment.getEmployee().getId());
                        WorkScheduleReason reason = isNewEmployee ? 
                                WorkScheduleReason.NEW_EMPLOYEE_VERIFICATION : 
                                WorkScheduleReason.CONTRACT_REQUIREMENT;
                        
                        log.info("[DEBUG] Work schedule reason: {}, isNewEmployee: {}", reason, isNewEmployee);
                        
                        // Create verification requirement if new employee
                        AssignmentVerification verification = null;
                        if (isNewEmployee) {
                                verification = verificationService.createVerificationRequirement(
                                        assignment, 
                                        reason.name()
                                );
                                log.info("[DEBUG] Created verification requirement: {}", verification.getId());
                        }
                        
                        // Calculate end date for work schedules
                        YearMonth yearMonth = YearMonth.from(startDate);
                        LocalDate endDate = yearMonth.atEndOfMonth();
                        
                        // Priority 1: Assignment endDate (for support workers with specific period)
                        if (assignment.getEndDate() != null) {
                                endDate = assignment.getEndDate();
                                log.info("[DEBUG] Using assignment endDate: {}", endDate);
                        }
                        // Priority 2: Contract endDate (if before calculated endDate)
                        else {
                                Contract contract = assignment.getContract();
                                if (contract != null && contract.getEndDate() != null && contract.getEndDate().isBefore(endDate)) {
                                        endDate = contract.getEndDate();
                                        log.info("[DEBUG] Using contract endDate: {}", endDate);
                                } else {
                                        log.info("[DEBUG] Using month endDate: {}", endDate);
                                }
                        }
                        
                        // Create work_schedules instead of attendances
                        workScheduleService.createWorkSchedulesForAssignment(
                                assignment,
                                reason,
                                verification != null ? verification.getId() : null,
                                startDate,
                                endDate
                        );
                        
                        log.info("[DEBUG] Created work_schedules for assignment {} from {} to {}", 
                                assignment.getId(), startDate, endDate);
                        
                        return; // STOP HERE - don't create attendances
                }
                
                // ===== NORMAL FLOW: Create attendances directly =====
                log.info("[DEBUG] Creating attendances directly for assignment {}", freshAssignment.getId());
                Contract contract = freshAssignment.getContract();
                log.info("[DEBUG] Contract: {}, contractType: {}", 
                        contract != null ? contract.getId() : "NULL",
                        contract != null ? contract.getContractType() : "NULL");
                List<Attendance> attendances = new ArrayList<>();

                // Nếu là hợp đồng ONE_TIME, tạo attendance cho từng ngày làm việc trong khoảng thời gian assignment
                if (contract != null && contract.getContractType() == ContractType.ONE_TIME) {
                        log.info("[DEBUG] Contract type is ONE_TIME, creating attendances for assignment period");
                        
                        // Với ONE_TIME, tạo attendance cho tất cả ngày làm việc trong khoảng startDate -> endDate của assignment
                        LocalDate assignmentEndDate = freshAssignment.getEndDate() != null ? 
                                freshAssignment.getEndDate() : startDate; // Nếu không có endDate, chỉ làm 1 ngày
                        
                        List<java.time.DayOfWeek> workingDays = freshAssignment.getWorkingDaysPerWeek().stream()
                                        .map(day -> java.time.DayOfWeek.valueOf(day.name()))
                                        .collect(Collectors.toList());
                        
                        LocalDate currentDate = startDate;
                        while (!currentDate.isAfter(assignmentEndDate)) {
                                if (workingDays.contains(currentDate.getDayOfWeek())) {
                                        boolean alreadyExists = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                                                        freshAssignment.getId(),
                                                        freshAssignment.getEmployee().getId(),
                                                        currentDate).isPresent();

                                        if (!alreadyExists) {
                                                Attendance attendance = Attendance.builder()
                                                                .employee(freshAssignment.getEmployee())
                                                                .assignment(freshAssignment)
                                                                .date(currentDate)
                                                                .deleted(false)
                                                                .workHours(java.math.BigDecimal.valueOf(8))
                                                                .bonus(java.math.BigDecimal.ZERO)
                                                                .penalty(java.math.BigDecimal.ZERO)
                                                                .supportCost(java.math.BigDecimal.ZERO)
                                                                .isOvertime(false)
                                                                .overtimeAmount(java.math.BigDecimal.ZERO)
                                                                .description("Tự động tạo từ phân công (Hợp đồng 1 lần)")
                                                                .createdAt(LocalDateTime.now())
                                                                .updatedAt(LocalDateTime.now())
                                                                .build();
                                                attendances.add(attendance);
                                        }
                                }
                                currentDate = currentDate.plusDays(1);
                        }
                } else {
                        YearMonth yearMonth = YearMonth.from(startDate);
                        LocalDate endDate = yearMonth.atEndOfMonth();
                        if (contract != null && contract.getEndDate() != null) {
                                log.info("[DEBUG] Contract endDate: {}, monthEnd: {}", contract.getEndDate(), endDate);
                                if (contract.getEndDate().isBefore(endDate)) {
                                        endDate = contract.getEndDate();
                                }
                        }
                        log.info("[DEBUG] Generating attendances from {} to {}, workingDays={}", 
                                startDate, endDate, freshAssignment.getWorkingDaysPerWeek());

                        List<java.time.DayOfWeek> workingDays = freshAssignment.getWorkingDaysPerWeek().stream()
                                        .map(day -> java.time.DayOfWeek.valueOf(day.name()))
                                        .collect(Collectors.toList());

                        LocalDate currentDate = startDate;
                        while (!currentDate.isAfter(endDate)) {
                                if (workingDays.contains(currentDate.getDayOfWeek())) {
                                        boolean alreadyExists = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                                                        freshAssignment.getId(),
                                                        freshAssignment.getEmployee().getId(),
                                                        currentDate).isPresent();
                                        if (!alreadyExists) {
                                                Attendance attendance = Attendance.builder()
                                                                .employee(freshAssignment.getEmployee())
                                                                .assignment(freshAssignment)
                                                                .date(currentDate)
                                                                .workHours(java.math.BigDecimal.valueOf(8))
                                                                .deleted(false)
                                                                .bonus(java.math.BigDecimal.ZERO)
                                                                .penalty(java.math.BigDecimal.ZERO)
                                                                .supportCost(java.math.BigDecimal.ZERO)
                                                                .isOvertime(false)
                                                                .overtimeAmount(java.math.BigDecimal.ZERO)
                                                                .description("Tự động tạo từ phân công")
                                                                .createdAt(LocalDateTime.now())
                                                                .updatedAt(LocalDateTime.now())
                                                                .build();
                                                attendances.add(attendance);
                                        }
                                }
                                currentDate = currentDate.plusDays(1);
                        }
                }
                // Lưu tất cả chấm công
                if (!attendances.isEmpty()) {
                        log.info("[DEBUG] ===== SAVING {} ATTENDANCES =====", attendances.size());
                        log.info("[DEBUG] Attendance details: assignmentId={}, startDate={}, endDate={}",
                                        freshAssignment.getId(), startDate,
                                        attendances.get(attendances.size() - 1).getDate());

                        attendanceRepository.saveAll(attendances);
                        log.info("[DEBUG] ===== ATTENDANCES SAVED SUCCESSFULLY =====");
                        log.info("Auto-generated {} attendances for assignmentId={} from {} to {}",
                                        attendances.size(), freshAssignment.getId(), startDate,
                                        attendances.get(attendances.size() - 1).getDate());

                        // Cập nhật workDays
                        freshAssignment.setWorkDays(attendances.size());

                        // Tính plannedDays:
                        // - Nếu hợp đồng là ONE_TIME -> plannedDays = số ngày làm việc thực tế trong khoảng assignment
                        // - Ngược lại -> plannedDays tính theo lịch làm việc của cả tháng
                        if (contract != null && contract.getContractType() == ContractType.ONE_TIME) {
                                // Với ONE_TIME, plannedDays = số attendance đã tạo
                                freshAssignment.setPlannedDays(attendances.size());
                        } else {
                                YearMonth ym = YearMonth.from(startDate);
                                LocalDate monthStart = ym.atDay(1);
                                LocalDate monthEnd = ym.atEndOfMonth();
                                
                                LocalDate periodStart = monthStart;
                                LocalDate periodEnd = monthEnd;

                                List<java.time.DayOfWeek> workingDays = freshAssignment.getWorkingDaysPerWeek().stream()
                                                .map(day -> java.time.DayOfWeek.valueOf(day.name()))
                                                .collect(Collectors.toList());

                                int planned = countWorkingDaysBetween(workingDays, periodStart, periodEnd);
                                freshAssignment.setPlannedDays(planned);
                        }
                        // Không cần save lại assignment nếu nó đang trong transaction với
                        // createAssignment
                        // JPA sẽ tự động save khi transaction commit
                }
        }

        private int countWorkingDaysBetween(List<java.time.DayOfWeek> workingDaysPerWeek, LocalDate start,
                        LocalDate end) {
                if (start == null || end == null || start.isAfter(end) || workingDaysPerWeek == null
                                || workingDaysPerWeek.isEmpty())
                        return 0;
                int count = 0;
                LocalDate cur = start;
                while (!cur.isAfter(end)) {
                        if (workingDaysPerWeek.contains(cur.getDayOfWeek()))
                                count++;
                        cur = cur.plusDays(1);
                }
                return count;
        }

        /**
         * Lấy N ngày làm việc đầu tiên trong khoảng [start, end] theo lịch workingDays.
         * Dùng để xác định đúng 5 ngày cần chụp ảnh xác minh nhân viên mới.
         */
        private List<LocalDate> getFirstNWorkingDays(LocalDate start, LocalDate end,
                        List<java.time.DayOfWeek> workingDays, int n) {
                List<LocalDate> result = new ArrayList<>();
                if (start == null || end == null || workingDays == null || workingDays.isEmpty() || n <= 0)
                        return result;
                LocalDate cur = start;
                while (!cur.isAfter(end) && result.size() < n) {
                        if (workingDays.contains(cur.getDayOfWeek()))
                                result.add(cur);
                        cur = cur.plusDays(1);
                }
                return result;
        }

        // ==================== LỊCH SỬ ĐIỀU ĐỘNG ====================

        @Override
        public List<AssignmentHistoryResponse> getReassignmentHistory(Long employeeId) {
                // Lấy cả lịch sử bị thay và lịch sử thay thế
                List<AssignmentHistory> replacedHistory = assignmentHistoryRepository
                                .findByReplacedEmployeeIdOrderByCreatedAtDesc(employeeId);
                List<AssignmentHistory> replacementHistory = assignmentHistoryRepository
                                .findByReplacementEmployeeIdOrderByCreatedAtDesc(employeeId);

                // Merge và loại bỏ duplicate
                List<AssignmentHistory> allHistory = new ArrayList<>();
                allHistory.addAll(replacedHistory);
                for (AssignmentHistory h : replacementHistory) {
                        if (!allHistory.contains(h)) {
                                allHistory.add(h);
                        }
                }

                // Sắp xếp theo thời gian mới nhất
                allHistory.sort((h1, h2) -> h2.getCreatedAt().compareTo(h1.getCreatedAt()));

                return allHistory.stream()
                                .map(this::mapHistoryToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public List<AssignmentHistoryResponse> getReassignmentHistoryByContract(Long contractId) {
                return assignmentHistoryRepository.findByContractIdOrderByCreatedAtDesc(contractId).stream()
                                .map(this::mapHistoryToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public PageResponse<com.company.company_clean_hub_be.dto.response.ReassignmentHistoryByContractResponse> getReassignmentHistoryByCustomerId(
                        Long customerId, Long contractId, Integer month, Integer year, int page, int pageSize) {
                Customer customer = customerRepository.findById(customerId)
                                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

                List<Contract> contracts = contractRepository.findByCustomerId(customer.getId());

                // If contractId provided, filter to that contract only
                if (contractId != null) {
                        contracts = contracts.stream().filter(c -> c.getId().equals(contractId))
                                        .collect(Collectors.toList());
                }

                int totalContracts = contracts.size();
                int safePage = Math.max(0, page);
                int safePageSize = Math.max(1, pageSize);
                int fromIndex = Math.min(totalContracts, safePage * safePageSize);
                int toIndex = Math.min(totalContracts, fromIndex + safePageSize);

                List<Contract> pageContracts = contracts.subList(fromIndex, toIndex);

                List<com.company.company_clean_hub_be.dto.response.ReassignmentHistoryByContractResponse> result = new ArrayList<>();

                org.springframework.data.domain.PageRequest pageable = org.springframework.data.domain.PageRequest
                                .of(safePage, safePageSize,
                                                org.springframework.data.domain.Sort.by("createdAt").descending());

                for (Contract contract : pageContracts) {
                        List<AssignmentHistoryResponse> mapped;

                        if (month == null && year == null) {
                                org.springframework.data.domain.Page<AssignmentHistory> pageHistories = assignmentHistoryRepository
                                                .findByContractIdOrderByCreatedAtDesc(contract.getId(), pageable);

                                mapped = pageHistories.getContent().stream()
                                                .map(this::mapHistoryToResponse)
                                                .collect(Collectors.toList());
                        } else {
                                List<AssignmentHistory> allHistories = assignmentHistoryRepository
                                                .findByContractIdOrderByCreatedAtDesc(contract.getId());
                                List<AssignmentHistory> filtered = allHistories.stream()
                                                .filter(h -> {
                                                        boolean mOk = month == null
                                                                        || h.getCreatedAt().getMonthValue() == month;
                                                        boolean yOk = year == null
                                                                        || h.getCreatedAt().getYear() == year;
                                                        return mOk && yOk;
                                                })
                                                .collect(Collectors.toList());

                                int histFrom = Math.min(filtered.size(), safePage * safePageSize);
                                int histTo = Math.min(filtered.size(), histFrom + safePageSize);
                                List<AssignmentHistory> pageList = filtered.subList(histFrom, histTo);

                                mapped = pageList.stream()
                                                .map(this::mapHistoryToResponse)
                                                .collect(Collectors.toList());
                        }

                        result.add(new com.company.company_clean_hub_be.dto.response.ReassignmentHistoryByContractResponse(
                                        contract.getId(),
                                        contract.getDescription(),
                                        mapped));
                }

                int totalPages = (int) Math.ceil((double) totalContracts / (double) safePageSize);
                boolean first = safePage == 0;
                boolean last = safePage >= totalPages - 1;

                PageResponse<com.company.company_clean_hub_be.dto.response.ReassignmentHistoryByContractResponse> pageResp = PageResponse.<com.company.company_clean_hub_be.dto.response.ReassignmentHistoryByContractResponse>builder()
                                .content(result)
                                .page(safePage)
                                .pageSize(safePageSize)
                                .totalElements(totalContracts)
                                .totalPages(totalPages)
                                .first(first)
                                .last(last)
                                .build();

                return pageResp;
        }

        @Override
        public AssignmentHistoryResponse getHistoryDetail(Long historyId) {
                AssignmentHistory history = assignmentHistoryRepository.findById(historyId)
                                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
                return mapHistoryToResponse(history);
        }

        @Override
        @Transactional
        public RollbackResponse rollbackReassignment(Long historyId) {
                // Tìm lịch sử điều động
                AssignmentHistory history = assignmentHistoryRepository.findActiveHistoryById(historyId)
                                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

                if (history.getStatus() == HistoryStatus.ROLLED_BACK) {
                        throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS); // Tạm dùng error này
                }

                // Lấy thông tin user đang rollback
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                User currentUser = userRepository.findByUsername(username).orElse(null);
                log.info("rollbackReassignment requested by {}: historyId={}", username, historyId);

                // Nếu user là Quản lý vùng (QLV) thì chỉ cho rollback các ngày hôm nay trở đi
                if (currentUser != null && currentUser.getRole() != null
                                && "QLV".equalsIgnoreCase(currentUser.getRole().getCode())) {
                        java.time.LocalDate today = java.time.LocalDate.now();
                        for (LocalDate d : history.getReassignmentDates()) {
                                if (d.isBefore(today)) {
                                        log.warn("QLV cannot rollback reassignment that includes past dates: historyId={}, date={}",
                                                        historyId, d);
                                        throw new AppException(ErrorCode.FORBIDDEN);
                                }
                        }
                }

                int restoredCount = 0;
                int removedCount = 0;

                // Rollback từng ngày
                for (LocalDate date : history.getReassignmentDates()) {
                        // Xóa attendance của người thay chỉ nếu trùng với temporary assignment
                        if (history.getNewAssignment() != null) {
                                Optional<Attendance> replacementAttendance = attendanceRepository
                                                .findByAssignmentAndEmployeeAndDate(
                                                                history.getNewAssignment().getId(),
                                                                history.getReplacementEmployeeId(), date);
                                if (replacementAttendance.isPresent()) {
                                        attendanceRepository.delete(replacementAttendance.get());
                                        removedCount++;
                                }
                        }

                        // Khôi phục attendance cho người bị thay
                        // Tìm assignment cũ để tạo lại attendance
                        Assignment oldAssignment = history.getOldAssignment();

                        Attendance restoredAttendance = Attendance.builder()
                                        .employee(oldAssignment.getEmployee())
                                        .assignment(oldAssignment)
                                        .date(date)
                                        .workHours(BigDecimal.valueOf(8))
                                        .bonus(java.math.BigDecimal.ZERO)
                                        .penalty(java.math.BigDecimal.ZERO)
                                        .supportCost(java.math.BigDecimal.ZERO)
                                        .isOvertime(false)
                                        .deleted(false)
                                        .description("Khôi phục sau rollback điều động")
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .build();

                        attendanceRepository.save(restoredAttendance);
                        restoredCount++;
                }

                // Cập nhật workDays cho assignment cũ (người bị thay)
                updateAssignmentWorkDays(history.getOldAssignment(), history.getReassignmentDates().get(0));

                // Chuyển trạng thái temporary assignment sang CANCELLED thay vì xóa
                Assignment rollbackAssignment = history.getNewAssignment();
                if (rollbackAssignment != null && rollbackAssignment.getAssignmentType() == AssignmentType.TEMPORARY) {
                        rollbackAssignment.setStatus(AssignmentStatus.CANCELLED);
                        assignmentRepository.save(rollbackAssignment);
                        log.info("Canceled temporary assignment {} during rollback", rollbackAssignment.getId());
                }

                // Đánh dấu history đã rollback
                history.setStatus(HistoryStatus.ROLLED_BACK);
                history.setRollbackBy(currentUser);
                history.setRollbackAt(LocalDateTime.now());
                assignmentHistoryRepository.save(history);

                log.info("rollbackReassignment completed by {}: historyId={}, restored={}, removed={}", username,
                                historyId, restoredCount, removedCount);

                return RollbackResponse.builder()
                                .success(true)
                                .message(String.format(
                                                "Đã rollback thành công điều động giữa %s và %s. Khôi phục %d ngày.",
                                                history.getReplacedEmployeeName(), history.getReplacementEmployeeName(),
                                                restoredCount))
                                .historyDetail(mapHistoryToResponse(history))
                                .restoredAttendances(restoredCount)
                                .removedAttendances(removedCount)
                                .build();
        }

        private void updateAssignmentWorkDays(Assignment assignment, LocalDate referenceDate) {
                YearMonth ym = YearMonth.from(referenceDate);
                LocalDate monthStart = ym.atDay(1);
                LocalDate monthEnd = ym.atEndOfMonth();

                int workDays = attendanceRepository
                                .findByAssignmentAndDateBetween(assignment.getId(), monthStart, monthEnd)
                                .size();

                assignment.setWorkDays(workDays);
                assignmentRepository.save(assignment);
        }

        private AssignmentHistoryResponse mapHistoryToResponse(AssignmentHistory history) {
                return AssignmentHistoryResponse.builder()
                                .id(history.getId())
                                .oldAssignmentId(history.getOldAssignment() != null ? history.getOldAssignment().getId()
                                                : null)
                                .newAssignmentId(history.getNewAssignment() != null ? history.getNewAssignment().getId()
                                                : null)
                                .replacedEmployeeId(history.getReplacedEmployeeId())
                                .replacedEmployeeName(history.getReplacedEmployeeName())
                                .replacementEmployeeId(history.getReplacementEmployeeId())
                                .replacementEmployeeName(history.getReplacementEmployeeName())
                                .contractId(history.getContractId())
                                .customerName(history.getCustomerName())
                                .reassignmentDates(history.getReassignmentDates())
                                .reassignmentType(history.getReassignmentType())
                                .notes(history.getNotes())
                                .status(history.getStatus())
                                .createdByUsername(history.getCreatedBy() != null ? history.getCreatedBy().getUsername()
                                                : null)
                                .createdAt(history.getCreatedAt())
                                .rollbackByUsername(
                                                history.getRollbackBy() != null ? history.getRollbackBy().getUsername()
                                                                : null)
                                .rollbackAt(history.getRollbackAt())
                                .build();
        }

        @Override
        @Transactional
        public AssignmentResponse terminateAssignment(Long assignmentId,
                        com.company.company_clean_hub_be.dto.request.TerminateAssignmentRequest request) {
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                log.info("[TERMINATE_ASSIGNMENT] Requested by {}: assignmentId={}, endDate={}, reason={}",
                                username, assignmentId, request.getEndDate(), request.getReason());

                // Kiểm tra assignment tồn tại
                Assignment assignment = assignmentRepository.findById(assignmentId)
                                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

                // Validate: assignment phải đang IN_PROGRESS hoặc SCHEDULED
                if (assignment.getStatus() != AssignmentStatus.IN_PROGRESS &&
                                assignment.getStatus() != AssignmentStatus.SCHEDULED) {
                        log.error("Cannot terminate assignment with status {}", assignment.getStatus());
                        throw new AppException(ErrorCode.INVALID_ASSIGNMENT_STATUS);
                }

                // Validate: endDate phải >= startDate
                if (request.getEndDate().isBefore(assignment.getStartDate())) {
                        log.error("End date {} is before start date {}", request.getEndDate(),
                                        assignment.getStartDate());
                        throw new AppException(ErrorCode.INVALID_REQUEST);
                }

                LocalDate endDate = request.getEndDate();
                LocalDate today = LocalDate.now();
                LocalDateTime now = LocalDateTime.now();

                // Luôn luôn xóa attendance sau endDate (backup trước khi xóa)
                List<Attendance> futureAttendances = attendanceRepository.findByAssignmentAndDateAfter(
                                assignment.getId(), endDate);

                log.info("[TERMINATE_ASSIGNMENT] Assignment {}: Found {} future attendances after {}",
                                assignmentId, futureAttendances.size(), endDate);

                // Backup và xóa các attendance trong tương lai
                for (Attendance att : futureAttendances) {
                        com.company.company_clean_hub_be.entity.DeletedAttendanceBackup backup = com.company.company_clean_hub_be.entity.DeletedAttendanceBackup
                                        .builder()
                                        .originalAttendanceId(att.getId())
                                        .assignmentId(assignment.getId())
                                        .employeeId(att.getEmployee() != null ? att.getEmployee().getId() : null)
                                        .date(att.getDate())
                                        .workHours(att.getWorkHours())
                                        .bonus(att.getBonus())
                                        .penalty(att.getPenalty())
                                        .supportCost(att.getSupportCost())
                                        .isOvertime(att.getIsOvertime())
                                        .overtimeAmount(att.getOvertimeAmount())
                                        .description(att.getDescription())
                                        .deletedBy(username)
                                        .deletedAt(now)
                                        .payload(null)
                                        .build();
                        deletedAttendanceBackupRepository.save(backup);
                        attendanceRepository.delete(att);
                }

                // Lưu endDate và reason
                assignment.setEndDate(endDate);

                if (request.getReason() != null && !request.getReason().isBlank()) {
                        String currentDesc = assignment.getDescription() != null ? assignment.getDescription() : "";
                        String prefix = (endDate.isAfter(today) || endDate.isEqual(today)) ? "Kết thúc (lên lịch)"
                                        : "Kết thúc";
                        assignment.setDescription(currentDesc + (currentDesc.isEmpty() ? "" : " | ") +
                                        prefix + ": " + request.getReason());
                }

                assignment.setUpdatedAt(now);

                // Tính lại workDays dựa trên attendance còn lại (sau khi đã xóa attendance
                // tương lai)
                YearMonth ym = YearMonth.from(today);
                LocalDate monthStart = ym.atDay(1);
                LocalDate monthEnd = ym.atEndOfMonth();

                // Đếm attendance còn lại từ đầu tháng đến min(endDate, today, monthEnd)
                LocalDate countUntil = endDate.isBefore(today) ? endDate : today;
                countUntil = countUntil.isBefore(monthEnd) ? countUntil : monthEnd;

                int currentWorkDays = attendanceRepository
                                .findByAssignmentAndDateBetween(assignment.getId(), monthStart, countUntil)
                                .size();
                assignment.setWorkDays(currentWorkDays);

                log.info("[TERMINATE_ASSIGNMENT] Recalculated workDays: assignmentId={}, workDays={} (counted from {} to {})",
                                assignmentId, currentWorkDays, monthStart, countUntil);

                // Nếu endDate là quá khứ -> chuyển sang TERMINATED ngay
                // Nếu endDate là hôm nay hoặc tương lai -> giữ IN_PROGRESS, scheduler sẽ xử lý
                // vào cuối ngày
                // Logic: Nếu endDate <= hôm nay -> TERMINATED ngay
                if (!endDate.isAfter(today)) {
                        assignment.setStatus(AssignmentStatus.TERMINATED);
                        log.info("[TERMINATE_ASSIGNMENT] Chuyển trạng thái TERMINATED ngay lập tức (endDate <= today): assignmentId={}, endDate={}",
                                        assignmentId, endDate);
                } else {
                        log.info("[TERMINATE_ASSIGNMENT] Giữ nguyên trạng thái (endDate > today): assignmentId={}, endDate={}, status={}",
                                        assignmentId, endDate, assignment.getStatus());
                }

                Assignment savedAssignment = assignmentRepository.save(assignment);

                log.info("[TERMINATE_ASSIGNMENT] Completed: assignmentId={}, employee={}, endDate={}, deletedAttendances={}, status={}",
                                assignmentId,
                                assignment.getEmployee().getName(),
                                endDate,
                                futureAttendances.size(),
                                savedAssignment.getStatus());

                return mapToResponse(savedAssignment);
        }

        @Override
        @Transactional
        public com.company.company_clean_hub_be.dto.response.RollbackTerminationResponse rollbackTermination(
                        Long assignmentId) {
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                log.info("[ROLLBACK_TERMINATION] Requested by {}: assignmentId={}", username, assignmentId);

                // Kiểm tra assignment tồn tại
                Assignment assignment = assignmentRepository.findById(assignmentId)
                                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

                boolean hasScheduledTermination = (assignment.getStatus() == AssignmentStatus.IN_PROGRESS
                                || assignment.getStatus() == AssignmentStatus.SCHEDULED)
                                && assignment.getEndDate() != null;

                // Validate: assignment phải đã kết thúc hoặc đang có lịch tạm dừng
                if (assignment.getStatus() != AssignmentStatus.TERMINATED && !hasScheduledTermination) {
                        log.error("Cannot rollback assignment with status {}", assignment.getStatus());
                        throw new AppException(ErrorCode.INVALID_ASSIGNMENT_STATUS);
                }

                Contract contract = assignment.getContract();
                Integer maxPositions = contract != null ? contract.getNumberOfEmployees() : null;
                if (contract != null
                                && maxPositions != null
                                && assignment.getAssignmentType() != AssignmentType.SUPPORT) {
                        Long activeEmployeeCount = assignmentRepository
                                        .countDistinctActiveEmployeesByContractExcludingTypeAndAssignmentIdNot(
                                                        contract.getId(),
                                                        AssignmentType.SUPPORT,
                                                        assignmentId);

                        if (activeEmployeeCount != null && activeEmployeeCount >= maxPositions) {
                                log.warn("[ROLLBACK_TERMINATION] Cannot rollback assignmentId={}: contractId={} is full (activeEmployees={}, maxPositions={})",
                                                assignmentId, contract.getId(), activeEmployeeCount, maxPositions);
                                throw new AppException(ErrorCode.ROLLBACK_TERMINATION_CONTRACT_FULL);
                        }
                }

                // Tìm các backup attendance
                List<com.company.company_clean_hub_be.entity.DeletedAttendanceBackup> backups = deletedAttendanceBackupRepository
                                .findByAssignmentId(assignmentId);

                if (backups == null || backups.isEmpty()) {
                        log.warn("[ROLLBACK_TERMINATION] No backups found for assignmentId={}", assignmentId);
                        backups = List.of();
                }

                log.info("[ROLLBACK_TERMINATION] Found {} backups to restore for assignmentId={}",
                                backups.size(), assignmentId);

                int restored = 0;
                for (com.company.company_clean_hub_be.entity.DeletedAttendanceBackup backup : backups) {
                        Attendance att = Attendance.builder()
                                        .employee(backup.getEmployeeId() != null ? employeeRepository
                                                        .findById(backup.getEmployeeId()).orElse(null) : null)
                                        .assignment(assignment)
                                        .date(backup.getDate())
                                        .workHours(backup.getWorkHours())
                                        .bonus(backup.getBonus() != null ? backup.getBonus() : BigDecimal.ZERO)
                                        .penalty(backup.getPenalty() != null ? backup.getPenalty() : BigDecimal.ZERO)
                                        .supportCost(backup.getSupportCost() != null ? backup.getSupportCost()
                                                        : BigDecimal.ZERO)
                                        .deleted(false)
                                        .isOvertime(backup.getIsOvertime() != null ? backup.getIsOvertime() : false)
                                        .overtimeAmount(backup.getOvertimeAmount() != null ? backup.getOvertimeAmount()
                                                        : BigDecimal.ZERO)
                                        .description(backup.getDescription())
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .build();
                        attendanceRepository.save(att);
                        deletedAttendanceBackupRepository.delete(backup);
                        restored++;
                }

                // Khôi phục assignment về trạng thái phù hợp với ngày bắt đầu
                AssignmentStatus restoredStatus = assignment.getStartDate().isAfter(LocalDate.now())
                                ? AssignmentStatus.SCHEDULED
                                : AssignmentStatus.IN_PROGRESS;
                assignment.setStatus(restoredStatus);
                assignment.setEndDate(null);

                // Xóa phần description về lý do kết thúc (nếu có)
                String desc = assignment.getDescription();
                if (desc != null && (desc.contains("Kết thúc:") || desc.contains("Kết thúc (lên lịch):"))) {
                        int idx = Math.max(
                                        desc.lastIndexOf(" | Kết thúc:"),
                                        desc.lastIndexOf(" | Kết thúc (lên lịch):"));
                        if (idx > 0) {
                                assignment.setDescription(desc.substring(0, idx));
                        } else if (desc.startsWith("Kết thúc:")
                                        || desc.startsWith("Kết thúc (lên lịch):")) {
                                assignment.setDescription("");
                        }
                }

                assignment.setUpdatedAt(LocalDateTime.now());

                // Cập nhật lại workDays
                YearMonth ym = YearMonth.from(LocalDate.now());
                LocalDate monthStart = ym.atDay(1);
                LocalDate monthEnd = ym.atEndOfMonth();
                int totalWorkDays = attendanceRepository
                                .findByAssignmentAndDateBetween(assignment.getId(), monthStart, monthEnd)
                                .size();
                assignment.setWorkDays(totalWorkDays);

                assignmentRepository.save(assignment);

                log.info("[ROLLBACK_TERMINATION] Completed: assignmentId={}, employee={}, restoredCount={}",
                                assignmentId, assignment.getEmployee().getName(), restored);

                return com.company.company_clean_hub_be.dto.response.RollbackTerminationResponse.builder()
                                .success(true)
                                .restoredCount(restored)
                                .assignmentId(assignmentId)
                                .employeeName(assignment.getEmployee().getName())
                                .message(String.format(
                                                "Đã khôi phục %d attendance và trả assignment về trạng thái IN_PROGRESS",
                                                restored))
                                .build();
        }

        // ───────────────────────────────────────────────────────────────
        // Kiểm tra và gửi notification nếu nhân viên trùng khung giờ
        // ───────────────────────────────────────────────────────────────
        private void checkAndNotifyTimeConflict(Assignment savedAssignment, Contract newContract) {
                // Bỏ qua nếu contract không có khung giờ
                if (newContract.getWorkStartTime() == null || newContract.getWorkEndTime() == null) {
                        return;
                }

                java.time.LocalTime newStart = newContract.getWorkStartTime();
                java.time.LocalTime newEnd = newContract.getWorkEndTime();
                Long employeeId = savedAssignment.getEmployee().getId();
                LocalDate checkFrom = savedAssignment.getStartDate();

                // Kiểm tra 7 ngày đầu (đại diện cho 1 tuần)
                java.time.DayOfWeek conflictDay = null;
                Contract conflictContract = null;

                for (int i = 0; i < 7; i++) {
                        LocalDate checkDate = checkFrom.plusDays(i);

                        // Chỉ kiểm tra ngày thuộc workingDays của contract mới
                        if (newContract.getWorkingDaysPerWeek() != null
                                        && !newContract.getWorkingDaysPerWeek().contains(checkDate.getDayOfWeek())) {
                                continue;
                        }

                        List<Assignment> conflicts = assignmentRepository.findAssignmentsWithTimeConflict(
                                        employeeId, checkDate, newStart, newEnd, savedAssignment.getId());

                        for (Assignment ca : conflicts) {
                                Contract cc = ca.getContract();
                                if (cc == null)
                                        continue;
                                // Kiểm tra ngày này có trong workingDays của contract cũ không
                                if (cc.getWorkingDaysPerWeek() != null
                                                && !cc.getWorkingDaysPerWeek().contains(checkDate.getDayOfWeek())) {
                                        continue;
                                }
                                conflictDay = checkDate.getDayOfWeek();
                                conflictContract = cc;
                                break;
                        }
                        if (conflictDay != null)
                                break;
                }

                if (conflictDay == null) {
                        return; // Không có conflict
                }

                // Dịch tên thứ
                String dayName = switch (conflictDay) {
                        case MONDAY -> "Thứ Hai";
                        case TUESDAY -> "Thứ Ba";
                        case WEDNESDAY -> "Thứ Tư";
                        case THURSDAY -> "Thứ Năm";
                        case FRIDAY -> "Thứ Sáu";
                        case SATURDAY -> "Thứ Bảy";
                        case SUNDAY -> "Chủ Nhật";
                };

                String title = "[TRUNG GIO] Cảnh báo trùng khung giờ làm việc";
                com.company.company_clean_hub_be.entity.Employee emp = (com.company.company_clean_hub_be.entity.Employee) savedAssignment
                                .getEmployee();
                String message = String.format(
                                "Nhân viên %s (%s) vừa được phân công vào Hợp đồng ID=%d (%s–%s). "
                                                + "Phát hiện trùng giờ với Hợp đồng ID=%d (%s–%s) vào ngày %s.",
                                emp.getName(),
                                emp.getEmployeeCode(),
                                newContract.getId(),
                                newStart, newEnd,
                                conflictContract.getId(),
                                conflictContract.getWorkStartTime(), conflictContract.getWorkEndTime(),
                                dayName);

                // Gửi notification cho tất cả QLT1 và các QLT2 được phân công quản lý khách hàng này   
                List<User> managers = new java.util.ArrayList<>(userRepository.findActiveUsersByRoleCode("QLT1"));
                if (newContract.getCustomer() != null) {
                        List<com.company.company_clean_hub_be.entity.CustomerAssignment> customerAssigns = customerAssignmentRepository.findByCustomerId(newContract.getCustomer().getId());
                        for (com.company.company_clean_hub_be.entity.CustomerAssignment ca : customerAssigns) {
                                if (ca.getManager() != null && ca.getManager().getRole() != null && 
                                    ("QLT2".equalsIgnoreCase(ca.getManager().getRole().getCode()) || "QLV".equalsIgnoreCase(ca.getManager().getRole().getCode()))) {
                                        if (managers.stream().noneMatch(m -> m.getId().equals(ca.getManager().getId()))) {
                                                managers.add(ca.getManager());
                                        }
                                }
                        }
                }
                log.warn("[NOTIFY][WORK_TIME_CONFLICT] Detected: employeeId={}, newContractId={}, conflictContractId={}, day={}",
                                employeeId, newContract.getId(), conflictContract.getId(), conflictDay);
                log.info("[NOTIFY][WORK_TIME_CONFLICT] Found {} manager(s) with role QLT1 to notify", managers.size());
                if (managers.isEmpty()) {
                        log.warn("[NOTIFY][WORK_TIME_CONFLICT] No QLT1 managers found — conflict notification will NOT be sent");
                }
                for (User manager : managers) {
                        log.info("[NOTIFY][WORK_TIME_CONFLICT] Sending to userId={} ({})",
                                        manager.getId(), manager.getUsername());
                        notificationService.createNotification(
                                        manager,
                                        com.company.company_clean_hub_be.entity.NotificationType.WORK_TIME_CONFLICT,
                                        title,
                                        message,
                                        savedAssignment.getEmployee().getId(),
                                        savedAssignment.getId(),
                                        newContract.getId());
                        log.info("[NOTIFY][WORK_TIME_CONFLICT] ✅ Sent successfully to userId={}", manager.getId());
                }
        }

        /**
         * Tính toán endDate cho assignment dựa trên type và request
         */
        private LocalDate calculateEndDate(AssignmentRequest request, AssignmentType assignmentType) {
                // Với SUPPORT assignment, endDate = ngày cuối cùng trong dates array
                if (assignmentType == AssignmentType.SUPPORT && request.getDates() != null && !request.getDates().isEmpty()) {
                        return request.getDates().stream()
                                .max(LocalDate::compareTo)
                                .orElse(request.getStartDate());
                }

                // Với các loại khác, không set endDate (null) - sẽ dùng contract endDate hoặc unlimited
                return null;
        }

        // ─── Task 19: Notification helpers ──────────────────────────────────────

        /**
         * Kiểm tra và gửi notification nếu hợp đồng thiếu nhân viên phụ trách.
         * So sánh số NV đang active với numberOfEmployees trong contract.
         */
        private void checkAndNotifyInsufficientStaff(Contract contract) {
                if (contract.getNumberOfEmployees() == null || contract.getNumberOfEmployees() <= 0) {
                        return;
                }
                Long currentCount = assignmentRepository.countActiveAssignmentsByContract(contract.getId());
                if (currentCount < contract.getNumberOfEmployees().longValue()) {
                        String title = "Thiếu nhân viên phụ trách hợp đồng";
                        String message = String.format(
                                "Hợp đồng '%s' (ID=%d) yêu cầu %d nhân viên nhưng hiện chỉ có %d nhân viên được phân công.",
                                contract.getDescription() != null ? contract.getDescription() : "Không có mô tả",
                                contract.getId(),
                                contract.getNumberOfEmployees(),
                                currentCount);

                        // Gửi cho tất cả QLT1, QLT2, và QLV
                        List<User> managers = userRepository.findByRoleCodeIn(List.of("QLT1", "QLT2", "QLV"));
                        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
                        for (User manager : managers) {
                                boolean exists = notificationRepository.existsByTypeAndRefContractIdAndRecipientIdAndCreatedAtAfter(
                                        NotificationType.INSUFFICIENT_STAFF, contract.getId(), manager.getId(), todayStart);
                                if (!exists) {
                                        notificationService.createNotification(
                                                manager,
                                                NotificationType.INSUFFICIENT_STAFF,
                                                title,
                                                message,
                                                null, null, contract.getId());
                                }
                        }
                        log.info("[NOTIFY][INSUFFICIENT_STAFF] Contract {} has {} employees, requires {}",
                                contract.getId(), currentCount, contract.getNumberOfEmployees());
                }
        }

        /**
         * Kiểm tra và gửi notification nếu phân công có lương vượt quá salaryNote quy định.
         * Duyệt tất cả SalaryNote của contract, so sánh với salaryAtTime của assignment.
         */
        private void checkAndNotifyAssignmentOverBudget(Assignment assignment) {
                Contract contract = assignment.getContract();
                if (contract == null) {
                        return;
                }
                List<SalaryNote> salaryNotes = salaryNoteRepository.findByContractId(contract.getId());
                if (salaryNotes == null || salaryNotes.isEmpty()) {
                        return;
                }

                BigDecimal assignmentSalary = assignment.getSalaryAtTime();
                if (assignmentSalary == null) {
                        return;
                }

                for (SalaryNote sn : salaryNotes) {
                        if (sn.getAmount() == null) {
                                continue;
                        }
                        if (assignmentSalary.compareTo(sn.getAmount()) > 0) {
                                Employee emp = (Employee) assignment.getEmployee();
                                String categoryLabel = sn.getCategory() != null
                                        ? sn.getCategory().getDescription()
                                        : "Không xác định";
                                String typeLabel = sn.getSalaryType() != null
                                        ? sn.getSalaryType().getDescription()
                                        : "Không xác định";
                                String title = "Phân công vượt ngân sách lương";
                                String message = String.format(
                                        "Nhân viên %s (%s) được phân công với mức lương %s VNĐ, "
                                                + "vượt quá ghi chú lương '%s - %s' (%s VNĐ) của hợp đồng '%s' (ID=%d).",
                                        emp.getName(),
                                        emp.getEmployeeCode(),
                                        String.format("%,.0f", assignmentSalary),
                                        categoryLabel, typeLabel,
                                        String.format("%,.0f", sn.getAmount()),
                                        contract.getDescription() != null ? contract.getDescription() : "Không có mô tả",
                                        contract.getId());

                                // Gửi cho tất cả QLT1, QLT2, và QLV
                                List<User> managers = userRepository.findByRoleCodeIn(List.of("QLT1", "QLT2", "QLV"));
                                LocalDateTime todayStart = LocalDate.now().atStartOfDay();
                                for (User manager : managers) {
                                        boolean exists = notificationRepository.existsByTypeAndContractIdAndEmployeeIdAndRecipientIdAndCreatedAtAfter(
                                                NotificationType.ASSIGNMENT_OVER_BUDGET,
                                                contract.getId(),
                                                assignment.getEmployee().getId(),
                                                manager.getId(),
                                                todayStart);
                                        if (!exists) {
                                                notificationService.createNotification(
                                                        manager,
                                                        NotificationType.ASSIGNMENT_OVER_BUDGET,
                                                        title,
                                                        message,
                                                        assignment.getEmployee().getId(),
                                                        assignment.getId(),
                                                        contract.getId());
                                        }
                                }
                                log.warn("[NOTIFY][ASSIGNMENT_OVER_BUDGET] Assignment {} salary {} exceeds SalaryNote {} amount {}",
                                        assignment.getId(), assignmentSalary, sn.getId(), sn.getAmount());
                                break; // Chỉ gửi 1 notification cho lần vượt đầu tiên
                        }
                }
        }
}
