package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.AssignmentRequest;
import com.company.company_clean_hub_be.dto.request.TemporaryReassignmentRequest;
import com.company.company_clean_hub_be.dto.response.*;
import com.company.company_clean_hub_be.entity.*;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.*;
import com.company.company_clean_hub_be.service.AssignmentService;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Slf4j
public class AssignmentServiceImpl implements AssignmentService {

        private final AssignmentRepository assignmentRepository;
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

        @Override
        public List<AssignmentResponse> getAllAssignments() {
                return assignmentRepository.findAll().stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Override
        public PageResponse<AssignmentResponse> getAssignmentsWithFilter(String keyword, int page, int pageSize) {
                Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
                Page<Assignment> assignmentPage = assignmentRepository.findByFilters(keyword, pageable);

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
                        // Nếu tạo cho ngày hôm nay nhưng đã qua 08:00 sáng thì QLV không được tạo
                        if (request.getStartDate().isEqual(today)) {
                                LocalTime now = LocalTime.now();
                                if (now.isAfter(LocalTime.of(8, 0))) {
                                        log.warn("QLV cannot create assignment for today after 08:00: now={}", now);
                                        throw new AppException(ErrorCode.QLV_CREATE_AFTER_ALLOWED_TIME);
                                }
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

                        workingDays = contract.getWorkingDaysPerWeek() != null
                                        ? new ArrayList<>(contract.getWorkingDaysPerWeek())
                                        : null;
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
                        log.info("Assignment startDate {} is in future, set status to SCHEDULED", request.getStartDate());
                } else {
                        // Phân công từ hôm nay trở về trước -> lấy từ request hoặc mặc định IN_PROGRESS
                        finalStatus = request.getStatus() != null ? request.getStatus() : AssignmentStatus.IN_PROGRESS;
                }

                // Kiểm tra nhân viên đã được phân công phụ trách hợp đồng này chưa (CHỈ check nếu finalStatus là IN_PROGRESS và scope là CONTRACT)
                if (scope == AssignmentScope.CONTRACT && AssignmentStatus.IN_PROGRESS.equals(finalStatus)) {
                        List<Assignment> existingAssignments = assignmentRepository
                                        .findActiveAssignmentByEmployeeAndContract(request.getEmployeeId(),
                                                        request.getContractId());
                        if (!existingAssignments.isEmpty()) {
                                throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS);
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

                Assignment assignment = Assignment.builder()
                                .employee(employee)
                                .contract(contract)
                                .scope(scope)
                                .startDate(request.getStartDate())
                                .status(finalStatus) 
                                .salaryAtTime(request.getSalaryAtTime())
                                .workingDaysPerWeek(workingDays)
                                .additionalAllowance(request.getAdditionalAllowance())
                                .description(request.getDescription())
                                .assignmentType(assignmentTypeParsed)
                                .assignedBy(creator)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                Assignment savedAssignment = assignmentRepository.save(assignment);

                // Tự động tạo chấm công cho cả SCHEDULED và IN_PROGRESS
                // Cron sau này chỉ chuyển status, không tạo attendance nữa
                if (AssignmentStatus.IN_PROGRESS.equals(finalStatus) || AssignmentStatus.SCHEDULED.equals(finalStatus)) {
                        // Nếu là SUPPORT: tạo chấm công theo danh sách ngày được gửi trong request
                        if (assignmentTypeParsed == AssignmentType.SUPPORT) {
                                List<java.time.LocalDate> requestedDates = request.getDates();
                                if (requestedDates != null && !requestedDates.isEmpty()) {
                                        List<Attendance> toSave = new ArrayList<>();
                                        for (java.time.LocalDate d : requestedDates) {
                                                boolean alreadyExists = attendanceRepository
                                                                .findByAssignmentAndEmployeeAndDate(
                                                                                savedAssignment.getId(),
                                                                                savedAssignment.getEmployee().getId(),
                                                                                d)
                                                                .isPresent();
                                                if (!alreadyExists) {
                                                        Attendance att = Attendance.builder()
                                                                        .employee(savedAssignment.getEmployee())
                                                                        .assignment(savedAssignment)
                                                                        .date(d)
                                                                        .workHours(java.math.BigDecimal.valueOf(8))
                                                                        .deleted(false)
                                                                        .bonus(java.math.BigDecimal.ZERO)
                                                                        .penalty(java.math.BigDecimal.ZERO)
                                                                        .supportCost(java.math.BigDecimal.ZERO)
                                                                        .isOvertime(false)
                                                                        .overtimeAmount(java.math.BigDecimal.ZERO)
                                                                        .description(request.getDescription() != null
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
                                                savedAssignment.setWorkDays((savedAssignment.getWorkDays() == null ? 0
                                                                : savedAssignment.getWorkDays()) + created);
                                                savedAssignment.setPlannedDays(
                                                                (savedAssignment.getPlannedDays() == null ? 0
                                                                                : savedAssignment.getPlannedDays())
                                                                                + created);
                                                assignmentRepository.save(savedAssignment);
                                                log.info("Created {} support attendances for assignmentId={}", created,
                                                                savedAssignment.getId());
                                        }
                                }
                        } else if (workingDays != null && !workingDays.isEmpty()) {
                                // Nếu startDate trong quá khứ, tạo assignment và attendance cho các tháng từ
                                // startDate đến hiện tại
                                YearMonth startMonth = YearMonth.from(request.getStartDate());
                                YearMonth currentMonth = YearMonth.from(today);

                                if (startMonth.isBefore(currentMonth)) {
                                        log.info("StartDate {} is in the past. Creating assignments and attendances from {} to {}",
                                                        request.getStartDate(), startMonth, currentMonth);

                                        // Tạo attendance cho tháng đầu tiên (savedAssignment đã được tạo ở trên)
                                        autoGenerateAttendancesForAssignment(savedAssignment, request.getStartDate());

                                        // Tạo assignment và attendance cho các tháng tiếp theo (từ tháng sau startMonth
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
                                                                        .status(AssignmentStatus.IN_PROGRESS)  // Các tháng trong quá khứ luôn là IN_PROGRESS
                                                                        .salaryAtTime(request.getSalaryAtTime())
                                                                        .workingDaysPerWeek(workingDays != null
                                                                                        ? new ArrayList<>(workingDays)
                                                                                        : null)
                                                                        .additionalAllowance(request
                                                                                        .getAdditionalAllowance())
                                                                        .description(request.getDescription())
                                                                        .assignmentType(assignmentTypeParsed)
                                                                        .assignedBy(savedAssignment.getAssignedBy())
                                                                        .createdAt(LocalDateTime.now())
                                                                        .updatedAt(LocalDateTime.now())
                                                                        .build();

                                                        Assignment savedMonthlyAssignment = assignmentRepository
                                                                        .save(monthlyAssignment);
                                                        log.info("Created monthly assignment for {}/{}: assignmentId={}",
                                                                        nextMonth.getMonthValue(), nextMonth.getYear(),
                                                                        savedMonthlyAssignment.getId());

                                                        // Tạo attendance cho tháng này
                                                        autoGenerateAttendancesForAssignment(savedMonthlyAssignment,
                                                                        monthStartDate);
                                                } else {
                                                        log.info("Assignment already exists for employee={}, contract={}, month={}/{}",
                                                                        request.getEmployeeId(),
                                                                        request.getContractId(),
                                                                        nextMonth.getMonthValue(), nextMonth.getYear());
                                                }

                                                nextMonth = nextMonth.plusMonths(1);
                                        }
                                } else {
                                        // StartDate là tháng hiện tại hoặc tương lai - chỉ tạo cho tháng đó
                                        autoGenerateAttendancesForAssignment(savedAssignment, request.getStartDate());
                                }
                        }
                }

                log.info("createAssignment completed by {}: assignmentId={} (employee={}, contract={})",
                                username,
                                savedAssignment.getId(),
                                savedAssignment.getEmployee().getId(),
                                savedAssignment.getContract() != null ? savedAssignment.getContract().getId() : null);

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

                // Validate active assignment uniqueness
                if (AssignmentStatus.IN_PROGRESS.equals(request.getStatus())) {
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

                // Update fields
                assignment.setEmployee(employee);
                assignment.setStartDate(request.getStartDate());
                assignment.setStatus(request.getStatus());
                assignment.setSalaryAtTime(request.getSalaryAtTime());

                assignment.setAdditionalAllowance(request.getAdditionalAllowance());
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

                // 4) Kiểm tra và xóa payroll nếu không còn assignment/attendance nào trong tháng/năm đó
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
                                                                remainingAssignments != null ? remainingAssignments.size()
                                                                                : 0,
                                                                remainingAttendances != null ? remainingAttendances.size()
                                                                                : 0,
                                                                hasRemainingData);

                                                // Nếu không còn assignment hoặc attendance nào, xóa payroll
                                                if (!hasRemainingData) {
                                                        Optional<com.company.company_clean_hub_be.entity.Payroll> payrollOpt = payrollRepository
                                                                        .findByEmployeeAndMonthAndYear(employeeId, month,
                                                                                        year);

                                                        if (payrollOpt.isPresent()) {
                                                                com.company.company_clean_hub_be.entity.Payroll payroll = payrollOpt
                                                                                .get();
                                                                Long payrollId = payroll.getId();
                                                                
                                                                // Xóa payment history trước khi xóa payroll
                                                                try {
                                                                        List<com.company.company_clean_hub_be.entity.PaymentHistory> paymentHistories = paymentHistoryRepository
                                                                                        .findByPayrollIdOrderByCreatedAtAsc(payrollId);
                                                                        if (paymentHistories != null && !paymentHistories.isEmpty()) {
                                                                                paymentHistoryRepository.deleteAll(paymentHistories);
                                                                                log.info("Deleted {} payment history records for payrollId={}", 
                                                                                                paymentHistories.size(), payrollId);
                                                                        }
                                                                } catch (Exception ex) {
                                                                        log.warn("Failed to delete payment history for payrollId={}: {}", 
                                                                                        payrollId, ex.getMessage());
                                                                }
                                                                
                                                                payrollRepository.delete(payroll);
                                                                log.info("Deleted payroll payrollId={} for employeeId={}, month={}, year={} (no remaining assignments/attendances)",
                                                                                payrollId, employeeId, month, year);
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
                                        employeeId, attendancesBeforeDelete != null ? attendancesBeforeDelete.size() : 0);
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
                System.out.println("Người thay: " + replacementEmployee.getName() + " (ID: "
                                + replacementEmployee.getId() + ")");

                Employee replacedEmployee = employeeRepository.findById(request.getReplacedEmployeeId())
                                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
                System.out.println("Người bị thay: " + replacedEmployee.getName() + " (ID: " + replacedEmployee.getId()
                                + ")");

                // Lấy thông tin user đang thực hiện
                User currentUser = userRepository.findByUsername(username).orElse(null);

                // Nếu người thực hiện là Quản lý vùng (code = 'QLV') thì chỉ được điều động
                // thay thế từ hôm nay trở về sau; nếu là hôm nay thì chỉ được trước 08:00
                if (currentUser != null && currentUser.getRole() != null
                                && "QLV".equalsIgnoreCase(currentUser.getRole().getCode())) {
                        LocalDate today = LocalDate.now();
                        LocalTime now = LocalTime.now();
                        for (LocalDate date : request.getDates()) {
                                if (date.isBefore(today)) {
                                        log.warn("QLV cannot perform temporary reassignment for past date: {}", date);
                                        throw new AppException(ErrorCode.FORBIDDEN);
                                }
                                if (date.isEqual(today) && !now.isBefore(LocalTime.of(8, 0))) {
                                        log.warn("QLV cannot perform temporary reassignment for today after 08:00: now={}", now);
                                        throw new AppException(ErrorCode.QLV_CREATE_AFTER_ALLOWED_TIME);
                                }
                        }
                }

                List<AttendanceResponse> createdAttendances = new ArrayList<>();
                List<AttendanceResponse> deletedAttendances = new ArrayList<>();

                // Để lưu vào history
                Assignment oldAssignment = null;
                Assignment newAssignment = null;

                // Xử lý từng ngày điều động
                for (LocalDate date : request.getDates()) {
                        System.out.println("\n--- Xử lý ngày: " + date + " ---");

                        List<Attendance> foundDeletedAttendances = new ArrayList<>();

                        // Nếu có replacedAssignmentId, CHỈ tìm attendance trong assignment đó
                        if (request.getReplacedAssignmentId() != null) {
                                Optional<Attendance> attOpt = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                                                request.getReplacedAssignmentId(), request.getReplacedEmployeeId(),
                                                date);
                                if (attOpt.isPresent()) {
                                        foundDeletedAttendances.add(attOpt.get());
                                        System.out.println("✓ Tìm thấy attendance từ replacedAssignmentId: "
                                                        + request.getReplacedAssignmentId());
                                } else {
                                        System.out.println("❌ LỖI: Không tìm thấy attendance cho assignmentId: "
                                                        + request.getReplacedAssignmentId() + " vào ngày " + date);
                                        throw new AppException(ErrorCode.REPLACED_EMPLOYEE_NO_ATTENDANCE);
                                }
                        } else {
                                // Không có replacedAssignmentId: tìm theo active assignment
                                System.out.println("→ Tìm attendance theo active assignment");
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
                        }

                        System.out.println("Attendance(s) của người bị thay (ID " + request.getReplacedEmployeeId()
                                        + ") vào ngày " + date + ": "
                                        + (foundDeletedAttendances.isEmpty() ? "KHÔNG CÓ"
                                                        : ("CÓ(" + foundDeletedAttendances.size() + ")")));

                        if (foundDeletedAttendances.isEmpty()) {
                                System.out.println("❌ LỖI: Người bị thay không có attendance vào ngày này");
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
                        System.out.println("Attendance tìm thấy: ID=" + deletedAttendance.getId()
                                        + ", workHours=" + deletedAttendance.getWorkHours()
                                        + ", isOvertime=" + deletedAttendance.getIsOvertime());

                        // Lưu lại old assignment cho history (lần đầu tiên)
                        if (oldAssignment == null) {
                                oldAssignment = replacedAssignmentEntity;
                        }

                        // Tạo temporary assignment
                        LocalDate today = LocalDate.now();
                        AssignmentStatus tempStatus = date.isAfter(today) ? AssignmentStatus.SCHEDULED : AssignmentStatus.IN_PROGRESS;
                        
                        Assignment temporaryAssignment = Assignment.builder()
                                        .employee(replacementEmployee)
                                        .contract(replacedAssignmentEntity.getContract())
                                        .assignmentType(AssignmentType.TEMPORARY)
                                        .workDays(1)
                                        .plannedDays(1)
                                        .salaryAtTime(request.getSalaryAtTime())
                                        .startDate(date)
                                        .status(tempStatus)  
                                        .description(request.getDescription() != null
                                                        ? request.getDescription()
                                                        : "Điều động tạm thời")
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .build();

                        Assignment savedTemporaryAssignment = assignmentRepository.save(temporaryAssignment);
                        System.out.println("✓ Đã tạo temporary assignment ID: " + savedTemporaryAssignment.getId());

                        // Lưu lại new assignment cho history (lần đầu tiên)
                        if (newAssignment == null) {
                                newAssignment = savedTemporaryAssignment;
                        }

                        // Xóa attendance cũ của người bị thay và tạo mới cho người thay
                        // Tạo attendance luôn cho cả SCHEDULED (cron sau chỉ chuyển status)
                        
                        // Kiểm tra người thay đã có attendance cùng assignment vào ngày này không (nếu
                        // có thì xóa để thay thế)
                        List<Attendance> replacementExisting = attendanceRepository.findAllByEmployeeAndDate(
                                        request.getReplacementEmployeeId(), date);

                        System.out.println("Kiểm tra người thay đã có attendance vào ngày " + date + ": "
                                        + (replacementExisting.isEmpty() ? "CHƯA CÓ"
                                                        : ("CÓ(" + replacementExisting.size() + ")")));

                        // Nếu có attendance của người thay trùng assignment của deletedAttendance thì
                        // xóa chúng
                        for (Attendance ex : replacementExisting) {
                                if (ex.getAssignment() != null && deletedAttendance.getAssignment() != null
                                                && ex.getAssignment().getId()
                                                                .equals(deletedAttendance.getAssignment().getId())) {
                                        System.out.println(
                                                        "⚠️ Người thay đã có attendance cùng assignment vào ngày này (ID: "
                                                                        + ex.getId() + ") - sẽ xóa để thay thế");
                                        attendanceRepository.delete(ex);
                                        System.out.println("✓ Đã xóa attendance cũ của người thay cùng assignment");
                                }
                        }

                        // Lưu attendance bị xóa
                        AttendanceResponse deletedAttendanceResponse = mapAttendanceToResponse(deletedAttendance);
                        deletedAttendances.add(deletedAttendanceResponse);
                        attendanceRepository.delete(deletedAttendance);
                        log.info("Deleted old attendance id={} for replacedEmployeeId={} on date={}",
                                        deletedAttendance.getId(), request.getReplacedEmployeeId(), date);

                        // Tạo attendance mới cho người thay
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
                        log.info("Created new attendance id={} for replacementEmployeeId={} on date={}",
                                        savedAttendance.getId(), request.getReplacementEmployeeId(), date);

                        AttendanceResponse createdAttendanceResponse = mapAttendanceToResponse(savedAttendance);
                        createdAttendances.add(createdAttendanceResponse);

                        // Cập nhật workDays cho assignment của người bị thay
                        YearMonth ym = YearMonth.from(date);
                        LocalDate monthStart = ym.atDay(1);
                        LocalDate monthEnd = ym.atEndOfMonth();

                        int replacedWorkDays = attendanceRepository
                                        .findByAssignmentAndDateBetween(replacedAssignmentEntity.getId(), monthStart,
                                                        monthEnd)
                                        .size();

                        replacedAssignmentEntity.setWorkDays(replacedWorkDays);
                        assignmentRepository.save(replacedAssignmentEntity);
                        log.info("Updated workDays for assignmentId={} -> {}", replacedAssignmentEntity.getId(),
                                        replacedWorkDays);
                }

                // Lưu lịch sử điều động
                if (oldAssignment != null && newAssignment != null) {
                        Contract contract = oldAssignment.getContract();
                        AssignmentHistory history = AssignmentHistory.builder()
                                        .oldAssignment(oldAssignment)
                                        .newAssignment(newAssignment)
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
                        LocalDate firstDate = request.getDates().get(0);
                        YearMonth ym = YearMonth.from(firstDate);
                        LocalDate start = ym.atDay(1);
                        LocalDate end = ym.atEndOfMonth();

                        log.info("Calculating monthly totals for month={} ({} -> {})", ym, start, end);

                        int replacementTotal = attendanceRepository
                                        .findByEmployeeAndDateBetween(request.getReplacementEmployeeId(), start, end)
                                        .size();
                        System.out.println("Tổng công người thay (ID " + request.getReplacementEmployeeId() + "): "
                                        + replacementTotal);

                        int replacedTotal = attendanceRepository
                                        .findByEmployeeAndDateBetween(request.getReplacedEmployeeId(), start, end)
                                        .size();
                        System.out.println("Tổng công người bị thay (ID " + request.getReplacedEmployeeId() + "): "
                                        + replacedTotal);

                        log.info("temporaryReassignment result: created={}, deleted={} (replacementTotal={}, replacedTotal={})",
                                        createdAttendances.size(), deletedAttendances.size(), replacementTotal,
                                        replacedTotal);

                        return TemporaryAssignmentResponse.builder()
                                        .createdAttendances(createdAttendances)
                                        .deletedAttendances(deletedAttendances)
                                        .replacementEmployeeTotalDays(replacementTotal)
                                        .replacedEmployeeTotalDays(replacedTotal)
                                        .processedDaysCount(createdAttendances.size())
                                        .message(String.format(
                                                        "Điều động thành công %d ngày: %s (+%d công, tổng: %d) thay %s (-%d công, tổng: %d)",
                                                        createdAttendances.size(),
                                                        replacementEmployee.getName(),
                                                        createdAttendances.size(),
                                                        replacementTotal,
                                                        replacedEmployee.getName(),
                                                        deletedAttendances.size(),
                                                        replacedTotal))
                                        .build();
                }

                log.warn("temporaryReassignment: no dates processed for request by {}", username);
                return TemporaryAssignmentResponse.builder()
                                .createdAttendances(createdAttendances)
                                .deletedAttendances(deletedAttendances)
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

                // Group assignments by contract
                Map<Long, List<Assignment>> assignmentsByContract = allAssignments.getContent().stream()
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
                        List<AssignmentResponse> assignmentResponses = contractAssignments.stream()
                                        .map(this::mapToResponse)
                                        .collect(Collectors.toList());

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
                                .employeeCode(employee.getEmployeeCode())
                                .cccd(employee.getCccd())
                                .address(employee.getAddress())
                                .name(employee.getName())
                                .bankAccount(employee.getBankAccount())
                                .bankName(employee.getBankName())
                                .description(employee.getDescription())
                                .employmentType(employee.getEmploymentType())
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

        /**
         * Tự động tạo chấm công cho assignment dựa vào workingDaysPerWeek
         * - Nếu hợp đồng ONE_TIME: chỉ tạo 1 attendance ngày đầu tiên
         * - Nếu hợp đồng khác: tạo từ startDate đến cuối tháng của startDate (hoặc cuối
         * tháng hiện tại nếu là tháng hiện tại)
         */
        private void autoGenerateAttendancesForAssignment(Assignment assignment, LocalDate startDate) {
                if (assignment.getWorkingDaysPerWeek() == null || assignment.getWorkingDaysPerWeek().isEmpty()) {
                        return;
                }

                Contract contract = assignment.getContract();
                List<Attendance> attendances = new ArrayList<>();

                // Nếu là hợp đồng ONE_TIME, chỉ tạo 1 attendance ngày đầu tiên
                if (contract != null && contract.getContractType() == ContractType.ONE_TIME) {
                        // Kiểm tra đã có chấm công cho assignment này vào ngày này chưa
                        boolean alreadyExists = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                                        assignment.getId(),
                                        assignment.getEmployee().getId(),
                                        startDate).isPresent();

                        if (!alreadyExists) {
                                Attendance attendance = Attendance.builder()
                                                .employee(assignment.getEmployee())
                                                .assignment(assignment)
                                                .date(startDate)
                                                .deleted(false)
                                                .workHours(java.math.BigDecimal.valueOf(8)) // Mặc định 8 giờ
                                                .bonus(java.math.BigDecimal.ZERO)
                                                .penalty(java.math.BigDecimal.ZERO)
                                                .supportCost(java.math.BigDecimal.ZERO)
                                                .isOvertime(false)
                                                .deleted(false)
                                                .overtimeAmount(java.math.BigDecimal.ZERO)
                                                .description("Tự động tạo từ phân công (Hợp đồng 1 lần)")
                                                .createdAt(LocalDateTime.now())
                                                .updatedAt(LocalDateTime.now())
                                                .build();

                                attendances.add(attendance);
                        }
                } else {
                        // Hợp đồng MONTHLY_FIXED hoặc MONTHLY_ACTUAL hoặc COMPANY scope: tạo theo
                        // workingDaysPerWeek
                        YearMonth yearMonth = YearMonth.from(startDate);

                        // Tính ngày kết thúc: lấy min của (cuối tháng, ngày kết thúc hợp đồng nếu có)
                        LocalDate endDate = yearMonth.atEndOfMonth();
                        if (contract != null && contract.getEndDate() != null) {
                                System.out.println("EndDate " + contract.getEndDate());
                                if (contract.getEndDate().isBefore(endDate)) {
                                        endDate = contract.getEndDate();
                                }
                        }

                        // Chuyển đổi DayOfWeek từ entity sang java.time.DayOfWeek
                        List<java.time.DayOfWeek> workingDays = assignment.getWorkingDaysPerWeek().stream()
                                        .map(day -> java.time.DayOfWeek.valueOf(day.name()))
                                        .collect(Collectors.toList());

                        // Duyệt qua tất cả các ngày từ startDate đến endDate
                        LocalDate currentDate = startDate;
                        while (!currentDate.isAfter(endDate)) {
                                // Kiểm tra ngày hiện tại có nằm trong danh sách ngày làm việc không
                                if (workingDays.contains(currentDate.getDayOfWeek())) {
                                        // Kiểm tra đã có chấm công cho assignment này vào ngày này chưa
                                        boolean alreadyExists = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                                                        assignment.getId(),
                                                        assignment.getEmployee().getId(),
                                                        currentDate).isPresent();

                                        // Nếu chưa tồn tại thì tạo mới
                                        if (!alreadyExists) {
                                                Attendance attendance = Attendance.builder()
                                                                .employee(assignment.getEmployee())
                                                                .assignment(assignment)
                                                                .date(currentDate)
                                                                .workHours(java.math.BigDecimal.valueOf(8)) // Mặc định
                                                                .deleted(false) // 8 giờ
                                                                .bonus(java.math.BigDecimal.ZERO)
                                                                .penalty(java.math.BigDecimal.ZERO)
                                                                .supportCost(java.math.BigDecimal.ZERO)
                                                                .isOvertime(false)
                                                                .deleted(false)
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
                        attendanceRepository.saveAll(attendances);
                        log.info("Auto-generated {} attendances for assignmentId={} from {} to {}",
                                        attendances.size(), assignment.getId(), startDate,
                                        attendances.get(attendances.size() - 1).getDate());

                        // Cập nhật workDays dựa vào số attendance vừa tạo cho assignment này
                        assignment.setWorkDays(attendances.size());

                        // Tính plannedDays:
                        // - Nếu hợp đồng là ONE_TIME -> plannedDays = 1
                        // - Ngược lại -> plannedDays tính theo lịch làm việc của cả tháng (1..endOfMonth)
                        YearMonth ym = YearMonth.from(startDate);
                        LocalDate monthStart = ym.atDay(1);
                        LocalDate monthEnd = ym.atEndOfMonth();

                        if (contract != null && contract.getContractType() == ContractType.ONE_TIME) {
                                assignment.setPlannedDays(1);
                        } else {
                                LocalDate periodStart = monthStart;
                                LocalDate periodEnd = monthEnd; // plannedDays covers the full month

                                // Chuyển danh sách ngày làm việc sang java.time.DayOfWeek
                                List<java.time.DayOfWeek> workingDays = assignment.getWorkingDaysPerWeek().stream()
                                                .map(day -> java.time.DayOfWeek.valueOf(day.name()))
                                                .collect(Collectors.toList());

                                int planned = countWorkingDaysBetween(workingDays, periodStart, periodEnd);
                                assignment.setPlannedDays(planned);
                        }
                        // Không cần save lại assignment nếu nó đang trong transaction với
                        // createAssignment
                        // JPA sẽ tự động save khi transaction commit
                }
        }

        private int countWorkingDaysBetween(List<java.time.DayOfWeek> workingDaysPerWeek, LocalDate start, LocalDate end) {
                if (start == null || end == null || start.isAfter(end) || workingDaysPerWeek == null || workingDaysPerWeek.isEmpty()) return 0;
                int count = 0;
                LocalDate cur = start;
                while (!cur.isAfter(end)) {
                        if (workingDaysPerWeek.contains(cur.getDayOfWeek())) count++;
                        cur = cur.plusDays(1);
                }
                return count;
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
                        Long customerId, Long contractId, int page, int pageSize) {
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
                        org.springframework.data.domain.Page<AssignmentHistory> pageHistories = assignmentHistoryRepository
                                        .findByContractIdOrderByCreatedAtDesc(contract.getId(), pageable);

                        List<AssignmentHistoryResponse> mapped = pageHistories.getContent().stream()
                                        .map(this::mapHistoryToResponse)
                                        .collect(Collectors.toList());

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
                Assignment newAssignment = history.getNewAssignment();
                if (newAssignment != null && newAssignment.getAssignmentType() == AssignmentType.TEMPORARY) {
                        newAssignment.setStatus(AssignmentStatus.CANCELLED);
                        assignmentRepository.save(newAssignment);
                        System.out.println(
                                        "Canceled temporary assignment " + newAssignment.getId() + " during rollback");
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
        public AssignmentResponse terminateAssignment(Long assignmentId, com.company.company_clean_hub_be.dto.request.TerminateAssignmentRequest request) {
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
                        log.error("End date {} is before start date {}", request.getEndDate(), assignment.getStartDate());
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
                        com.company.company_clean_hub_be.entity.DeletedAttendanceBackup backup = 
                                com.company.company_clean_hub_be.entity.DeletedAttendanceBackup.builder()
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
                        String prefix = (endDate.isAfter(today) || endDate.isEqual(today)) ? "Kết thúc (lên lịch)" : "Kết thúc";
                        assignment.setDescription(currentDesc + (currentDesc.isEmpty() ? "" : " | ") + 
                                prefix + ": " + request.getReason());
                }
                
                assignment.setUpdatedAt(now);

                // Tính lại workDays dựa trên attendance còn lại (sau khi đã xóa attendance tương lai)
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
                // Nếu endDate là hôm nay hoặc tương lai -> giữ IN_PROGRESS, scheduler sẽ xử lý vào cuối ngày
                if (endDate.isBefore(today)) {
                        assignment.setStatus(AssignmentStatus.TERMINATED);
                        log.info("[TERMINATE_ASSIGNMENT] Terminated immediately (endDate < today): assignmentId={}, endDate={}", 
                                assignmentId, endDate);
                } else {
                        // endDate hôm nay hoặc trong tương lai - giữ status hiện tại (IN_PROGRESS)
                        log.info("[TERMINATE_ASSIGNMENT] Scheduled termination (endDate today or in future): assignmentId={}, endDate={}, status stays IN_PROGRESS", 
                                assignmentId, endDate);
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
        public com.company.company_clean_hub_be.dto.response.RollbackTerminationResponse rollbackTermination(Long assignmentId) {
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                log.info("[ROLLBACK_TERMINATION] Requested by {}: assignmentId={}", username, assignmentId);

                // Kiểm tra assignment tồn tại
                Assignment assignment = assignmentRepository.findById(assignmentId)
                        .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

                // Validate: assignment phải đang TERMINATED
                if (assignment.getStatus() != AssignmentStatus.TERMINATED) {
                        log.error("Cannot rollback assignment with status {}", assignment.getStatus());
                        throw new AppException(ErrorCode.INVALID_ASSIGNMENT_STATUS);
                }

                // Tìm các backup attendance
                List<com.company.company_clean_hub_be.entity.DeletedAttendanceBackup> backups = 
                        deletedAttendanceBackupRepository.findByAssignmentId(assignmentId);
                
                if (backups == null || backups.isEmpty()) {
                        log.warn("[ROLLBACK_TERMINATION] No backups found for assignmentId={}", assignmentId);
                        return com.company.company_clean_hub_be.dto.response.RollbackTerminationResponse.builder()
                                .success(false)
                                .restoredCount(0)
                                .assignmentId(assignmentId)
                                .employeeName(assignment.getEmployee().getName())
                                .message("Không tìm thấy backup attendance để khôi phục")
                                .build();
                }

                log.info("[ROLLBACK_TERMINATION] Found {} backups to restore for assignmentId={}", 
                        backups.size(), assignmentId);

                int restored = 0;
                for (com.company.company_clean_hub_be.entity.DeletedAttendanceBackup backup : backups) {
                        Attendance att = Attendance.builder()
                                .employee(backup.getEmployeeId() != null ? 
                                        employeeRepository.findById(backup.getEmployeeId()).orElse(null) : null)
                                .assignment(assignment)
                                .date(backup.getDate())
                                .workHours(backup.getWorkHours())
                                .bonus(backup.getBonus() != null ? backup.getBonus() : BigDecimal.ZERO)
                                .penalty(backup.getPenalty() != null ? backup.getPenalty() : BigDecimal.ZERO)
                                .supportCost(backup.getSupportCost() != null ? backup.getSupportCost() : BigDecimal.ZERO)
                                .deleted(false)
                                .isOvertime(backup.getIsOvertime() != null ? backup.getIsOvertime() : false)
                                .overtimeAmount(backup.getOvertimeAmount() != null ? backup.getOvertimeAmount() : BigDecimal.ZERO)
                                .description(backup.getDescription())
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();
                        attendanceRepository.save(att);
                        deletedAttendanceBackupRepository.delete(backup);
                        restored++;
                }

                // Khôi phục assignment về IN_PROGRESS
                assignment.setStatus(AssignmentStatus.IN_PROGRESS);
                assignment.setEndDate(null);
                
                // Xóa phần description về lý do kết thúc (nếu có)
                String desc = assignment.getDescription();
                if (desc != null && desc.contains("Kết thúc:")) {
                        int idx = desc.lastIndexOf(" | Kết thúc:");
                        if (idx > 0) {
                                assignment.setDescription(desc.substring(0, idx));
                        } else if (desc.startsWith("Kết thúc:")) {
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
                        .message(String.format("Đã khôi phục %d attendance và trả assignment về trạng thái IN_PROGRESS", restored))
                        .build();
        }
}
