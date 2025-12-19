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
        private final AssignmentHistoryRepository assignmentHistoryRepository;
        private final UserRepository userRepository;

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

                        // Kiểm tra nhân viên đã được phân công phụ trách hợp đồng này chưa (status
                        // IN_PROGRESS)
                        if (AssignmentStatus.IN_PROGRESS.equals(request.getStatus())) {
                                List<Assignment> existingAssignments = assignmentRepository
                                                .findActiveAssignmentByEmployeeAndContract(request.getEmployeeId(),
                                                                request.getContractId());
                                if (!existingAssignments.isEmpty()) {
                                        throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS);
                                }
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
                                .status(request.getStatus())
                                .salaryAtTime(request.getSalaryAtTime())
                                .workingDaysPerWeek(workingDays)
                                .additionalAllowance(request.getAdditionalAllowance())
                                .description(request.getDescription())
                                .assignmentType(assignmentTypeParsed)
                                .createdAt(LocalDateTime.now())
                                .updatedAt(LocalDateTime.now())
                                .build();

                Assignment savedAssignment = assignmentRepository.save(assignment);

                // Tự động tạo chấm công nếu có workingDaysPerWeek và status là IN_PROGRESS
                if (AssignmentStatus.IN_PROGRESS.equals(request.getStatus()) &&
                                workingDays != null &&
                                !workingDays.isEmpty()) {

                        // Nếu startDate trong quá khứ, tạo assignment và attendance cho các tháng từ
                        // startDate đến hiện tại
                        LocalDate today = LocalDate.now();
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
                                                                .status(request.getStatus())
                                                                .salaryAtTime(request.getSalaryAtTime())
                                                                .workingDaysPerWeek(workingDays != null
                                                                                ? new ArrayList<>(workingDays)
                                                                                : null)
                                                                .additionalAllowance(request.getAdditionalAllowance())
                                                                .description(request.getDescription())
                                                                .assignmentType(assignmentTypeParsed)
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
                                                                request.getEmployeeId(), request.getContractId(),
                                                                nextMonth.getMonthValue(), nextMonth.getYear());
                                        }

                                        nextMonth = nextMonth.plusMonths(1);
                                }
                        } else {
                                // StartDate là tháng hiện tại hoặc tương lai - chỉ tạo cho tháng đó
                                autoGenerateAttendancesForAssignment(savedAssignment, request.getStartDate());
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
                assignmentRepository.save(updatedAssignment);

                log.debug("[ASSIGNMENT][UPDATE] Recalculated workDays={}, assignmentId={}",
                                totalWorkDays, updatedAssignment.getId());

                log.info("[ASSIGNMENT][UPDATE] Finish update assignment, id={}", updatedAssignment.getId());

                return mapToResponse(updatedAssignment);
        }

        @Override
        public void deleteAssignment(Long id) {
                Assignment assignment = assignmentRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
                String username = SecurityContextHolder.getContext().getAuthentication().getName();
                log.info("deleteAssignment by {}: assignmentId={}", username, id);
                assignmentRepository.delete(assignment);
                log.info("deleteAssignment completed: assignmentId={}", id);
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
                        Assignment temporaryAssignment = Assignment.builder()
                                        .employee(replacementEmployee)
                                        .contract(replacedAssignmentEntity.getContract())
                                        .assignmentType(AssignmentType.TEMPORARY)
                                        .workDays(1)
                                        .plannedDays(1)
                                        .salaryAtTime(request.getSalaryAtTime())
                                        .startDate(date)
                                        .status(AssignmentStatus.IN_PROGRESS)
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
        public PageResponse<AssignmentResponse> getAssignmentsByContract(Long contractId, com.company.company_clean_hub_be.entity.AssignmentStatus status, Integer month, Integer year,
                                                int page, int pageSize) {
                contractRepository.findById(contractId)
                                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

                int safePage = Math.max(0, page <= 0 ? 0 : page - 1);
                int safePageSize = Math.max(1, pageSize);
                Pageable pageable = PageRequest.of(safePage, safePageSize, Sort.by("startDate").descending());

                Page<Assignment> assignmentPage = assignmentRepository.findByContractIdWithFilters(contractId, status, month,
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
                                .status(assignment.getStatus())
                                .salaryAtTime(assignment.getSalaryAtTime())
                                .workDays(assignment.getWorkDays())
                                .plannedDays(assignment.getPlannedDays())
                                .workingDaysPerWeek(assignment.getWorkingDaysPerWeek())
                                .additionalAllowance(assignment.getAdditionalAllowance())
                                .description(assignment.getDescription())
                                .createdAt(assignment.getCreatedAt())
                                .updatedAt(assignment.getUpdatedAt())
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
                                                .workHours(java.math.BigDecimal.valueOf(8)) // Mặc định 8 giờ
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
                                                                                                            // 8 giờ
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
                        attendanceRepository.saveAll(attendances);
                        log.info("Auto-generated {} attendances for assignmentId={} from {} to {}",
                                        attendances.size(), assignment.getId(), startDate,
                                        attendances.get(attendances.size() - 1).getDate());

                        // Cập nhật workDays và plannedDays dựa vào số attendance vừa tạo cho assignment
                        // này
                        assignment.setWorkDays(attendances.size());
                        assignment.setPlannedDays(attendances.size());
                        // Không cần save lại assignment nếu nó đang trong transaction với
                        // createAssignment
                        // JPA sẽ tự động save khi transaction commit
                }
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
                                        .description("Khôi phục sau rollback điều động")
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .build();

                        attendanceRepository.save(restoredAttendance);
                        restoredCount++;
                }

                // Cập nhật workDays cho assignment cũ (người bị thay)
                updateAssignmentWorkDays(history.getOldAssignment(), history.getReassignmentDates().get(0));

                // Chuyển trạng thái temporary assignment sang CANCELED thay vì xóa
                Assignment newAssignment = history.getNewAssignment();
                if (newAssignment != null && newAssignment.getAssignmentType() == AssignmentType.TEMPORARY) {
                        newAssignment.setStatus(AssignmentStatus.CANCELED);
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
}
