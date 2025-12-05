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
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
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

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Contract contract = contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Kiểm tra ngày bắt đầu assignment không được trước ngày bắt đầu contract
        if (request.getStartDate().isBefore(contract.getStartDate())) {
            throw new AppException(ErrorCode.ASSIGNMENT_START_DATE_BEFORE_CONTRACT);
        }

        // Kiểm tra nhân viên đã được phân công phụ trách hợp đồng này chưa (status IN_PROGRESS)
        if (AssignmentStatus.IN_PROGRESS.equals(request.getStatus())) {
            List<Assignment> existingAssignments = assignmentRepository
                    .findActiveAssignmentByEmployeeAndContract(request.getEmployeeId(), request.getContractId());
            if (!existingAssignments.isEmpty()) {
                throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS);
            }
        }

        Assignment assignment = Assignment.builder()
                .employee(employee)
                .contract(contract)
                .startDate(request.getStartDate())
                .status(request.getStatus())
                .salaryAtTime(request.getSalaryAtTime())
                .workingDaysPerWeek(contract.getWorkingDaysPerWeek() != null 
                    ? new ArrayList<>(contract.getWorkingDaysPerWeek()) 
                    : null)
                .additionalAllowance(request.getAdditionalAllowance())
                .description(request.getDescription())
                .assignmentType(AssignmentType.valueOf(request.getAssignmentType()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Assignment savedAssignment = assignmentRepository.save(assignment);

        // Tự động tạo chấm công nếu có workingDaysPerWeek và status là IN_PROGRESS
        if (AssignmentStatus.IN_PROGRESS.equals(request.getStatus()) && 
            contract.getWorkingDaysPerWeek() != null &&
            !contract.getWorkingDaysPerWeek().isEmpty()) {
            
            autoGenerateAttendancesForAssignment(savedAssignment, request.getStartDate());
        }

        return mapToResponse(savedAssignment);
    }

    @Override
    @Transactional
    public AssignmentResponse updateAssignment(Long id, AssignmentRequest request) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Contract contract = contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Kiểm tra ngày bắt đầu assignment không được trước ngày bắt đầu contract
        if (request.getStartDate().isBefore(contract.getStartDate())) {
            throw new AppException(ErrorCode.ASSIGNMENT_START_DATE_BEFORE_CONTRACT);
        }

        // Kiểm tra nhân viên đã được phân công phụ trách hợp đồng này chưa (status IN_PROGRESS)
        if (AssignmentStatus.IN_PROGRESS.equals(request.getStatus())) {
            List<Assignment> existingAssignments = assignmentRepository
                    .findActiveAssignmentByEmployeeAndContractAndIdNot(
                            request.getEmployeeId(), 
                            request.getContractId(), 
                            id
                    );
            if (!existingAssignments.isEmpty()) {
                throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS);
            }
        }

        assignment.setEmployee(employee);
        assignment.setContract(contract);
        assignment.setStartDate(request.getStartDate());
        assignment.setStatus(request.getStatus());
        assignment.setSalaryAtTime(request.getSalaryAtTime());
        assignment.setWorkingDaysPerWeek(contract.getWorkingDaysPerWeek() != null 
            ? new ArrayList<>(contract.getWorkingDaysPerWeek()) 
            : null);
        assignment.setAdditionalAllowance(request.getAdditionalAllowance());
        assignment.setDescription(request.getDescription());
        assignment.setUpdatedAt(LocalDateTime.now());

        Assignment updatedAssignment = assignmentRepository.save(assignment);

        // Tính lại workDays dựa vào số chấm công thực tế trong tháng
        YearMonth ym = YearMonth.from(request.getStartDate());
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();
        
        int totalWorkDays = attendanceRepository
                .findByEmployeeAndDateBetween(employee.getId(), monthStart, monthEnd)
                .size();
        
        updatedAssignment.setWorkDays(totalWorkDays);
        assignmentRepository.save(updatedAssignment);

        return mapToResponse(updatedAssignment);
    }


    @Override
    public void deleteAssignment(Long id) {
        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        assignmentRepository.delete(assignment);
    }

    @Override
    @Transactional
    public TemporaryAssignmentResponse temporaryReassignment(TemporaryReassignmentRequest request) {

        System.out.println("=== BẮT ĐẦU ĐIỀU ĐỘNG TẠM THỜI ===");
        System.out.println("Request: replacementEmployeeId=" + request.getReplacementEmployeeId()
                + ", replacedEmployeeId=" + request.getReplacedEmployeeId()
                + ", dates=" + request.getDates()
                + ", salaryAtTime=" + request.getSalaryAtTime());
        
        Employee replacementEmployee = employeeRepository.findById(request.getReplacementEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
        System.out.println("Người thay: " + replacementEmployee.getName() + " (ID: " + replacementEmployee.getId() + ")");

        Employee replacedEmployee = employeeRepository.findById(request.getReplacedEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
        System.out.println("Người bị thay: " + replacedEmployee.getName() + " (ID: " + replacedEmployee.getId() + ")");

        // Lấy thông tin user đang thực hiện
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(username).orElse(null);

        List<AttendanceResponse> createdAttendances = new ArrayList<>();
        List<AttendanceResponse> deletedAttendances = new ArrayList<>();
        
        // Để lưu vào history
        Assignment oldAssignment = null;
        Assignment newAssignment = null;

        // Xử lý từng ngày điều động
        for (LocalDate date : request.getDates()) {
            System.out.println("\n--- Xử lý ngày: " + date + " ---");

            // Tìm attendance của người bị thay
            Optional<Attendance> deletedAttendanceOpt = attendanceRepository.findByEmployeeAndDate(
                    request.getReplacedEmployeeId(),
                    date
            );

            System.out.println("Attendance của người bị thay (ID " + request.getReplacedEmployeeId() + ") vào ngày " + date + ": "
                    + (deletedAttendanceOpt.isPresent() ? "CÓ" : "KHÔNG CÓ"));
            if (!deletedAttendanceOpt.isPresent()) {
                System.out.println("❌ LỖI: Người bị thay không có attendance vào ngày này");
                throw new AppException(ErrorCode.REPLACED_EMPLOYEE_NO_ATTENDANCE);
            }

            Attendance deletedAttendance = deletedAttendanceOpt.get();
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

            // Check nếu người thay đã có công ngày này
            Optional<Attendance> existingAttendance = attendanceRepository.findByEmployeeAndDate(
                    request.getReplacementEmployeeId(), date
            );

            System.out.println("Kiểm tra người thay đã có attendance vào ngày " + date + ": "
                    + (existingAttendance.isPresent() ? "CÓ RỒI" : "CHƯA CÓ"));
            
            // Nếu người thay đã có attendance vào ngày này, xóa attendance cũ đi (cho phép điều động lại)
            if (existingAttendance.isPresent()) {
                System.out.println("⚠️ Người thay đã có attendance vào ngày này (ID: " + existingAttendance.get().getId() + ")");
                System.out.println("→ Xóa attendance cũ để thay thế bằng attendance mới từ điều động");
                attendanceRepository.delete(existingAttendance.get());
                System.out.println("✓ Đã xóa attendance cũ của người thay");
            }

            // Lưu attendance bị xóa
            AttendanceResponse deletedAttendanceResponse = mapAttendanceToResponse(deletedAttendance);
            deletedAttendances.add(deletedAttendanceResponse);
            attendanceRepository.delete(deletedAttendance);
            System.out.println("✓ Đã xóa attendance cũ ID: " + deletedAttendance.getId());

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
                            : "Điều động thay thế " + replacedEmployee.getName() + " ngày " + date)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();

            Attendance savedAttendance = attendanceRepository.save(newAttendance);
            System.out.println("✓ Đã tạo attendance mới ID: " + savedAttendance.getId());

            AttendanceResponse createdAttendanceResponse = mapAttendanceToResponse(savedAttendance);
            createdAttendances.add(createdAttendanceResponse);

            // Cập nhật workDays cho assignment của người bị thay
            YearMonth ym = YearMonth.from(date);
            LocalDate monthStart = ym.atDay(1);
            LocalDate monthEnd = ym.atEndOfMonth();

            int replacedWorkDays = attendanceRepository
                    .findByEmployeeAndDateBetween(request.getReplacedEmployeeId(), monthStart, monthEnd)
                    .size();

            replacedAssignmentEntity.setWorkDays(replacedWorkDays);
            assignmentRepository.save(replacedAssignmentEntity);
            System.out.println("✓ Đã cập nhật workDays của assignment ID " + replacedAssignmentEntity.getId() + " = " + replacedWorkDays);
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
                    .contractId(contract.getId())
                    .customerName(contract.getCustomer().getName())
                    .reassignmentDates(new ArrayList<>(request.getDates()))
                    .reassignmentType(ReassignmentType.TEMPORARY)
                    .notes(request.getDescription())
                    .status(HistoryStatus.ACTIVE)
                    .createdBy(currentUser)
                    .build();
            
            assignmentHistoryRepository.save(history);
            System.out.println("✓ Đã lưu lịch sử điều động ID: " + history.getId());
        }

        // Tính công trong tháng (lấy tháng của ngày đầu tiên)
        if (!request.getDates().isEmpty()) {
            LocalDate firstDate = request.getDates().get(0);
            YearMonth ym = YearMonth.from(firstDate);
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();

            System.out.println("\n=== TÍNH TỔNG CÔNG TRONG THÁNG ===");
            System.out.println("Tháng: " + ym + " (từ " + start + " đến " + end + ")");

            int replacementTotal = attendanceRepository
                    .findByEmployeeAndDateBetween(request.getReplacementEmployeeId(), start, end).size();
            System.out.println("Tổng công người thay (ID " + request.getReplacementEmployeeId() + "): " + replacementTotal);

            int replacedTotal = attendanceRepository
                    .findByEmployeeAndDateBetween(request.getReplacedEmployeeId(), start, end).size();
            System.out.println("Tổng công người bị thay (ID " + request.getReplacedEmployeeId() + "): " + replacedTotal);

            System.out.println("\n=== KẾT QUA ===");
            System.out.println("Số ngày đã xử lý thành công: " + createdAttendances.size());
            System.out.println("Số attendance đã tạo: " + createdAttendances.size());
            System.out.println("Số attendance đã xóa: " + deletedAttendances.size());

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
                            replacedTotal
                    ))
                    .build();
        }

        System.out.println("\n⚠️ KHÔNG CÓ NGÀY NÀO ĐƯỢC XỬ LÝ");
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
    public List<com.company.company_clean_hub_be.dto.response.CustomerResponse> getCustomersByEmployee(Long employeeId) {
        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        List<com.company.company_clean_hub_be.entity.Customer> customers = assignmentRepository.findActiveCustomersByEmployee(employeeId);

        return customers.stream().map(c -> com.company.company_clean_hub_be.dto.response.CustomerResponse.builder()
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
        List<Assignment> assignments = assignmentRepository.findActiveAssignmentsByEmployee(employeeId, java.time.LocalDate.now());

        return assignments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<AssignmentResponse> getAssignmentsByEmployeeMonthYear(Long employeeId, Integer month, Integer year) {
        // validate employee exists
        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
        List<Assignment> assignments = assignmentRepository.findAssignmentsByEmployeeAndMonthAndYear(employeeId, month,year);

        return assignments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<com.company.company_clean_hub_be.dto.response.EmployeeResponse> getEmployeesNotAssignedToCustomer(
            Long customerId, int page, int pageSize) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        Page<Employee> employeePage = employeeRepository.findEmployeesNotAssignedToCustomer(customerId, pageable);

        List<com.company.company_clean_hub_be.dto.response.EmployeeResponse> items = employeePage.getContent().stream()
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

    private com.company.company_clean_hub_be.dto.response.EmployeeResponse mapEmployeeToResponse(Employee employee) {
        return com.company.company_clean_hub_be.dto.response.EmployeeResponse.builder()
                .id(employee.getId())
                .employeeCode(employee.getEmployeeCode())
                .cccd(employee.getCccd())
                .address(employee.getAddress())
                .name(employee.getName())
                .bankAccount(employee.getBankAccount())
                .bankName(employee.getBankName())
                .description(employee.getDescription())
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
                .assignmentType(assignment != null && assignment.getAssignmentType() != null ? assignment.getAssignmentType().name() : null)
                .customerId(assignment != null && assignment.getContract().getCustomer() != null ? assignment.getContract().getCustomer().getId() : null)
                .customerName(assignment != null && assignment.getContract() != null ? assignment.getContract().getCustomer().getName() : null)
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
        return AssignmentResponse.builder()
                .id(assignment.getId())
                .employeeId(assignment.getEmployee().getId())
                .employeeName(assignment.getEmployee().getName())
                .employeeCode(assignment.getEmployee().getEmployeeCode())
                .assignmentType(assignment.getAssignmentType().name())
                .customerId(assignment.getContract().getCustomer().getId())
                .customerName(assignment.getContract().getCustomer().getName())
                .customerCode(assignment.getContract().getCustomer().getCustomerCode())
                .contractId(assignment.getContract().getId())
                .contractDescription(assignment.getContract().getDescription())
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
     * - Nếu hợp đồng khác: tạo từ startDate đến cuối tháng hiện tại
     */
    private void autoGenerateAttendancesForAssignment(Assignment assignment, LocalDate startDate) {
        if (assignment.getWorkingDaysPerWeek() == null || assignment.getWorkingDaysPerWeek().isEmpty()) {
            return;
        }

        Contract contract = assignment.getContract();
        List<Attendance> attendances = new ArrayList<>();

        // Nếu là hợp đồng ONE_TIME, chỉ tạo 1 attendance ngày đầu tiên
        if (contract.getContractType() == ContractType.ONE_TIME) {
            // Kiểm tra đã có chấm công ngày này chưa
            boolean alreadyExists = attendanceRepository.findByEmployeeAndDate(
                    assignment.getEmployee().getId(),
                    startDate
            ).isPresent();

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
            // Hợp đồng MONTHLY_FIXED hoặc MONTHLY_ACTUAL: tạo theo workingDaysPerWeek
            // Tính ngày cuối tháng
            YearMonth yearMonth = YearMonth.from(startDate);
            LocalDate endDate = yearMonth.atEndOfMonth();

            // Chuyển đổi DayOfWeek từ entity sang java.time.DayOfWeek
            List<java.time.DayOfWeek> workingDays = assignment.getWorkingDaysPerWeek().stream()
                    .map(day -> java.time.DayOfWeek.valueOf(day.name()))
                    .collect(Collectors.toList());

            // Duyệt qua tất cả các ngày từ startDate đến cuối tháng
            LocalDate currentDate = startDate;
            while (!currentDate.isAfter(endDate)) {
                // Kiểm tra ngày hiện tại có nằm trong danh sách ngày làm việc không
                if (workingDays.contains(currentDate.getDayOfWeek())) {
                    // Kiểm tra đã có chấm công ngày này chưa
                    boolean alreadyExists = attendanceRepository.findByEmployeeAndDate(
                            assignment.getEmployee().getId(),
                            currentDate
                    ).isPresent();

                    // Nếu chưa tồn tại thì tạo mới
                    if (!alreadyExists) {
                        Attendance attendance = Attendance.builder()
                                .employee(assignment.getEmployee())
                                .assignment(assignment)
                                .date(currentDate)
                                .workHours(java.math.BigDecimal.valueOf(8)) // Mặc định 8 giờ
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

            // Tính lại workDays dựa vào số chấm công thực tế trong tháng
            YearMonth ym = YearMonth.from(startDate);
            LocalDate monthStart = ym.atDay(1);
            LocalDate monthEnd = ym.atEndOfMonth();

            int totalWorkDays = attendanceRepository
                    .findByEmployeeAndDateBetween(assignment.getEmployee().getId(), monthStart, monthEnd)
                    .size();

            assignment.setWorkDays(totalWorkDays);
            assignment.setPlannedDays(totalWorkDays);
            // Không cần save lại assignment nếu nó đang trong transaction với createAssignment
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

        int restoredCount = 0;
        int removedCount = 0;

        // Rollback từng ngày
        for (LocalDate date : history.getReassignmentDates()) {
            // Xóa attendance của người thay
            Optional<Attendance> replacementAttendance = attendanceRepository.findByEmployeeAndDate(
                    history.getReplacementEmployeeId(), date);
            if (replacementAttendance.isPresent()) {
                attendanceRepository.delete(replacementAttendance.get());
                removedCount++;
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
            System.out.println("Canceled temporary assignment " + newAssignment.getId() + " during rollback");
        }
        
        // Đánh dấu history đã rollback
        history.setStatus(HistoryStatus.ROLLED_BACK);
        history.setRollbackBy(currentUser);
        history.setRollbackAt(LocalDateTime.now());
        assignmentHistoryRepository.save(history);

        return RollbackResponse.builder()
                .success(true)
                .message(String.format("Đã rollback thành công điều động giữa %s và %s. Khôi phục %d ngày.",
                        history.getReplacedEmployeeName(), history.getReplacementEmployeeName(), restoredCount))
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
                .findByEmployeeAndDateBetween(assignment.getEmployee().getId(), monthStart, monthEnd)
                .size();

        assignment.setWorkDays(workDays);
        assignmentRepository.save(assignment);
    }

    private AssignmentHistoryResponse mapHistoryToResponse(AssignmentHistory history) {
        return AssignmentHistoryResponse.builder()
                .id(history.getId())
                .oldAssignmentId(history.getOldAssignment() != null ? history.getOldAssignment().getId() : null)
                .newAssignmentId(history.getNewAssignment() != null ? history.getNewAssignment().getId() : null)
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
                .createdByUsername(history.getCreatedBy() != null ? history.getCreatedBy().getUsername() : null)
                .createdAt(history.getCreatedAt())
                .rollbackByUsername(history.getRollbackBy() != null ? history.getRollbackBy().getUsername() : null)
                .rollbackAt(history.getRollbackAt())
                .build();
    }
}
