package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.AssignmentRequest;
import com.company.company_clean_hub_be.dto.request.TemporaryReassignmentRequest;
import com.company.company_clean_hub_be.dto.response.AssignmentResponse;
import com.company.company_clean_hub_be.dto.response.AttendanceResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.TemporaryAssignmentResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.Customer;
import com.company.company_clean_hub_be.entity.Employee;
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
import java.util.stream.Collectors;

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

        Assignment assignment = Assignment.builder()
                .employee(employee)
                .customer(customer)
                .startDate(request.getStartDate())
                .status(request.getStatus())
                .salaryAtTime(request.getSalaryAtTime())
                .workDays(request.getWorkDays())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return mapToResponse(assignmentRepository.save(assignment));
    }

    @Override
    public AssignmentResponse updateAssignment(Long id, AssignmentRequest request) {

        Assignment assignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

        assignment.setEmployee(employee);
        assignment.setCustomer(customer);
        assignment.setStartDate(request.getStartDate());
        assignment.setStatus(request.getStatus());
        assignment.setSalaryAtTime(request.getSalaryAtTime());
        assignment.setWorkDays(request.getWorkDays());
        assignment.setDescription(request.getDescription());
        assignment.setUpdatedAt(LocalDateTime.now());

        return mapToResponse(assignmentRepository.save(assignment));
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

        Assignment replacementAssignment = replacementAssignments.get(0);

        // Check nếu người thay đã có công
        attendanceRepository.findByEmployeeAndDate(
                request.getReplacementEmployeeId(), request.getDate()
        ).ifPresent(a -> {
            throw new AppException(ErrorCode.ATTENDANCE_ALREADY_EXISTS);
        });

        // Tìm attendance của người bị thay
        Attendance deletedAttendance = attendanceRepository.findByEmployeeAndDate(
                request.getReplacedEmployeeId(),
                request.getDate()
        ).orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_NOT_FOUND));

        AttendanceResponse deletedAttendanceResponse = mapAttendanceToResponse(deletedAttendance);
        attendanceRepository.delete(deletedAttendance);

        // Tạo attendance mới cho người thay
        Attendance newAttendance = Attendance.builder()
                .employee(replacementEmployee)
                .assignment(replacementAssignment)
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

    private AttendanceResponse mapAttendanceToResponse(Attendance attendance) {
        return AttendanceResponse.builder()
                .id(attendance.getId())
                .employeeId(attendance.getEmployee().getId())
                .employeeName(attendance.getEmployee().getName())
                .employeeCode(attendance.getEmployee().getEmployeeCode())
                .assignmentId(attendance.getAssignment().getId())
                .customerId(attendance.getAssignment().getCustomer().getId())
                .customerName(attendance.getAssignment().getCustomer().getName())
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
                .customerId(assignment.getCustomer().getId())
                .customerName(assignment.getCustomer().getName())
                .customerCode(assignment.getCustomer().getCustomerCode())
                .startDate(assignment.getStartDate())
                .status(assignment.getStatus())
                .salaryAtTime(assignment.getSalaryAtTime())
                .workDays(assignment.getWorkDays())
                .description(assignment.getDescription())
                .createdAt(assignment.getCreatedAt())
                .updatedAt(assignment.getUpdatedAt())
                .build();
    }
}
