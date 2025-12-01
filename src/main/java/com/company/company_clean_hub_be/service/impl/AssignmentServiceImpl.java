package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.AssignmentRequest;
import com.company.company_clean_hub_be.dto.request.TemporaryReassignmentRequest;
import com.company.company_clean_hub_be.dto.response.AssignmentResponse;
import com.company.company_clean_hub_be.dto.response.AttendanceResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.TemporaryAssignmentResponse;
import com.company.company_clean_hub_be.entity.*;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.CustomerRepository;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.service.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

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
    private final AttendanceRepository attendanceRepository;

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
    public AssignmentResponse createAssignment(AssignmentRequest request) {

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

        // Kiểm tra nhân viên đã được phân công phụ trách khách hàng này chưa (status ACTIVE)
        if ("ACTIVE".equals(request.getStatus())) {
            List<Assignment> existingAssignments = assignmentRepository
                    .findActiveAssignmentByEmployeeAndCustomer(request.getEmployeeId(), request.getCustomerId());
            if (!existingAssignments.isEmpty()) {
                throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS);
            }
        }

        Assignment assignment = Assignment.builder()
                .employee(employee)
                .customer(customer)
                .startDate(request.getStartDate())
                .status(request.getStatus())
                .salaryAtTime(request.getSalaryAtTime())
                .workingDaysPerWeek(request.getWorkingDaysPerWeek())
                .additionalAllowance(request.getAdditionalAllowance())
                .description(request.getDescription())
                .assignmentType(AssignmentType.valueOf(request.getAssignmentType()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Assignment savedAssignment = assignmentRepository.save(assignment);

        // Tự động tạo chấm công nếu có workingDaysPerWeek và status là ACTIVE
        if ("ACTIVE".equals(request.getStatus()) && 
            request.getWorkingDaysPerWeek() != null && 
            !request.getWorkingDaysPerWeek().isEmpty()) {
            
            autoGenerateAttendancesForAssignment(savedAssignment, request.getStartDate());
        }

        return mapToResponse(savedAssignment);
    }

    @Override
    public AssignmentResponse updateAssignment(Long id, AssignmentRequest request) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

        // Kiểm tra nhân viên đã được phân công phụ trách khách hàng này chưa (status ACTIVE)
        if ("ACTIVE".equals(request.getStatus())) {
            List<Assignment> existingAssignments = assignmentRepository
                    .findActiveAssignmentByEmployeeAndCustomerAndIdNot(
                            request.getEmployeeId(), 
                            request.getCustomerId(), 
                            id
                    );
            if (!existingAssignments.isEmpty()) {
                throw new AppException(ErrorCode.ASSIGNMENT_ALREADY_EXISTS);
            }
        }

        assignment.setEmployee(employee);
        assignment.setCustomer(customer);
        assignment.setStartDate(request.getStartDate());
        assignment.setStatus(request.getStatus());
        assignment.setSalaryAtTime(request.getSalaryAtTime());
        assignment.setWorkingDaysPerWeek(request.getWorkingDaysPerWeek());
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
    public TemporaryAssignmentResponse temporaryReassignment(TemporaryReassignmentRequest request) {

        Employee replacementEmployee = employeeRepository.findById(request.getReplacementEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Employee replacedEmployee = employeeRepository.findById(request.getReplacedEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        List<Assignment> replacementAssignments =
                assignmentRepository.findActiveAssignmentsByEmployee(request.getReplacementEmployeeId(), request.getDate());

        if (replacementAssignments.isEmpty()) {
            throw new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND);
        }
        // Tìm attendance của người bị thay
        Attendance deletedAttendance = attendanceRepository.findByEmployeeAndDate(
                request.getReplacedEmployeeId(),
                request.getDate()
        ).orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_NOT_FOUND));
        Optional<Assignment> replacedAssignment = assignmentRepository.findById(deletedAttendance.getAssignment().getId());
        Assignment replacedAssignmentEntity = replacedAssignment
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        Assignment temporaryAssignment = Assignment.builder()
                .employee(replacementEmployee)
                .customer(replacedAssignmentEntity.getCustomer())
                .assignmentType(AssignmentType.TEMPORARY)
                .workDays(1)
                .salaryAtTime(request.getSalaryAtTime())
                .startDate(request.getDate())
                .status("ACTIVE")
                .description(request.getDescription() != null
                        ? request.getDescription()
                        : "Điều động tạm thời")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Assignment savedTemporaryAssignment = assignmentRepository.save(temporaryAssignment);

        // Check nếu người thay đã có công
        attendanceRepository.findByEmployeeAndDate(
                request.getReplacementEmployeeId(), request.getDate()
        ).ifPresent(a -> {
            throw new AppException(ErrorCode.ATTENDANCE_ALREADY_EXISTS);
        });

        AttendanceResponse deletedAttendanceResponse = mapAttendanceToResponse(deletedAttendance);
        attendanceRepository.delete(deletedAttendance);

        // Tạo attendance mới cho người thay
        Attendance newAttendance = Attendance.builder()
                .employee(replacementEmployee)
                .assignment(savedTemporaryAssignment)
                .date(request.getDate())
                .workHours(java.math.BigDecimal.valueOf(8))
                .bonus(java.math.BigDecimal.ZERO)
                .penalty(java.math.BigDecimal.ZERO)
                .supportCost(java.math.BigDecimal.ZERO)
                .isOvertime(false)
                .overtimeAmount(java.math.BigDecimal.ZERO)
                .description(request.getDescription() != null
                        ? request.getDescription()
                        : "Điều động thay thế " + replacedEmployee.getName() + " ngày " + request.getDate())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Attendance savedAttendance = attendanceRepository.save(newAttendance);
        AttendanceResponse createdAttendanceResponse = mapAttendanceToResponse(savedAttendance);

        // Tính công trong tháng
        YearMonth ym = YearMonth.from(request.getDate());
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        int replacementTotal = attendanceRepository
                .findByEmployeeAndDateBetween(request.getReplacementEmployeeId(), start, end).size();

        int replacedTotal = attendanceRepository
                .findByEmployeeAndDateBetween(request.getReplacedEmployeeId(), start, end).size();
        replacedAssignmentEntity.setWorkDays(replacedTotal);
        assignmentRepository.save(replacedAssignmentEntity);
        return TemporaryAssignmentResponse.builder()
                .createdAttendance(createdAttendanceResponse)
                .deletedAttendance(deletedAttendanceResponse)
                .replacementEmployeeTotalDays(replacementTotal)
                .replacedEmployeeTotalDays(replacedTotal)
                .message(String.format(
                        "Điều động thành công: %s (+1 công, tổng: %d) thay %s (-1 công, tổng: %d) ngày %s",
                        replacementEmployee.getName(),
                        replacementTotal,
                        replacedEmployee.getName(),
                        replacedTotal,
                        request.getDate()
                ))
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
        return AttendanceResponse.builder()
                .id(attendance.getId())
                .employeeId(attendance.getEmployee().getId())
                .employeeName(attendance.getEmployee().getName())
                .employeeCode(attendance.getEmployee().getEmployeeCode())
                .assignmentId(assignment.getId())
                .assignmentType(assignment.getAssignmentType() != null ? assignment.getAssignmentType().name() : null)
                .customerId(assignment.getCustomer().getId())
                .customerName(assignment.getCustomer().getName())
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
                .customerId(assignment.getCustomer().getId())
                .customerName(assignment.getCustomer().getName())
                .customerCode(assignment.getCustomer().getCustomerCode())
                .startDate(assignment.getStartDate())
                .status(assignment.getStatus())
                .salaryAtTime(assignment.getSalaryAtTime())
                .workDays(assignment.getWorkDays())
                .workingDaysPerWeek(assignment.getWorkingDaysPerWeek())
                .additionalAllowance(assignment.getAdditionalAllowance())
                .description(assignment.getDescription())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }

    /**
     * Tự động tạo chấm công cho assignment dựa vào workingDaysPerWeek
     * Từ startDate đến cuối tháng hiện tại
     */
    private void autoGenerateAttendancesForAssignment(Assignment assignment, LocalDate startDate) {
        if (assignment.getWorkingDaysPerWeek() == null || assignment.getWorkingDaysPerWeek().isEmpty()) {
            return;
        }

        // Tính ngày cuối tháng
        YearMonth yearMonth = YearMonth.from(startDate);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<Attendance> attendances = new ArrayList<>();

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
            assignmentRepository.save(assignment);
        }
    }
}
