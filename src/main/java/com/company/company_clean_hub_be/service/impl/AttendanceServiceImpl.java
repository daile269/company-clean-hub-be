package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.AttendanceRequest;
import com.company.company_clean_hub_be.dto.request.AutoAttendanceRequest;
import com.company.company_clean_hub_be.dto.request.TemporaryAssignmentRequest;
import com.company.company_clean_hub_be.dto.response.AttendanceResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.TemporaryAssignmentResponse;
import com.company.company_clean_hub_be.dto.response.TotalDaysResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.Attendance;
import com.company.company_clean_hub_be.entity.Employee;
import com.company.company_clean_hub_be.entity.User;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
import com.company.company_clean_hub_be.repository.EmployeeRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.service.AttendanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AttendanceServiceImpl implements AttendanceService {
    private final AttendanceRepository attendanceRepository;
    private final EmployeeRepository employeeRepository;
    private final AssignmentRepository assignmentRepository;
    private final UserRepository userRepository;

    @Override
    public AttendanceResponse createAttendance(AttendanceRequest request) {
        String username = "anonymous";
        try {
                org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
                if (auth != null && auth.getName() != null) username = auth.getName();
        } catch (Exception ignored) {
        }
        log.debug("createAttendance requested by {}: employeeId={}, assignmentId={}, date={}", username, request.getEmployeeId(), request.getAssignmentId(), request.getDate());
        // Kiểm tra nhân viên đã chấm công ngày này chưa
        attendanceRepository.findByEmployeeAndDate(request.getEmployeeId(), request.getDate())
                .ifPresent(a -> {
                    throw new AppException(ErrorCode.ATTENDANCE_ALREADY_EXISTS);
                });

        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        User approver = null;
        if (request.getApprovedBy() != null) {
            approver = userRepository.findById(request.getApprovedBy())
                    .orElse(null);
        }

        Attendance attendance = Attendance.builder()
                .assignment(assignment)
                .date(request.getDate())
                .workHours(request.getWorkHours())
                .bonus(request.getBonus())
                .penalty(request.getPenalty())
                .supportCost(request.getSupportCost())
                .isOvertime(request.getIsOvertime())
                .overtimeAmount(request.getOvertimeAmount())
                .approvedBy(approver)
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Attendance savedAttendance = attendanceRepository.save(attendance);
        log.debug("createAttendance completed by {}: attendanceId={}", username, savedAttendance.getId());
        return mapToResponse(savedAttendance);
    }

    @Override
    public List<AttendanceResponse> autoGenerateAttendances(AutoAttendanceRequest request) {
        log.debug("autoGenerateAttendances requested: employeeId={}, assignmentId={}, month={}, year={}", request.getEmployeeId(), request.getAssignmentId(), request.getMonth(), request.getYear());
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        // Tính ngày đầu và cuối tháng
        YearMonth yearMonth = YearMonth.of(request.getYear(), request.getMonth());
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<Attendance> attendances = new ArrayList<>();
        List<LocalDate> excludeDates = request.getExcludeDates() != null ? 
                request.getExcludeDates() : new ArrayList<>();

        // Duyệt qua tất cả các ngày trong tháng
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            // Bỏ qua Chủ nhật (có thể tùy chỉnh)
            boolean isSunday = currentDate.getDayOfWeek() == DayOfWeek.SUNDAY;
            
            // Bỏ qua ngày trong danh sách excludeDates
            boolean isExcluded = excludeDates.contains(currentDate);
            
            // Kiểm tra đã có chấm công ngày này chưa
            boolean alreadyExists = attendanceRepository.findByEmployeeAndDate(
                    request.getEmployeeId(), 
                    currentDate
            ).isPresent();

            // Nếu không phải chủ nhật, không trong danh sách loại trừ, và chưa tồn tại
                        if (!isSunday && !isExcluded && !alreadyExists) {
                                Attendance attendance = Attendance.builder()
                                                .assignment(assignment)
                                                .date(currentDate)
                                                .workHours(java.math.BigDecimal.valueOf(8)) // Mặc định 8 giờ
                                                .bonus(java.math.BigDecimal.ZERO)
                                                .penalty(java.math.BigDecimal.ZERO)
                                                .supportCost(java.math.BigDecimal.ZERO)
                                                .isOvertime(false)
                                                .overtimeAmount(java.math.BigDecimal.ZERO)
                                                .createdAt(LocalDateTime.now())
                                                .updatedAt(LocalDateTime.now())
                                                .build();
                
                                attendances.add(attendance);
                        }

            currentDate = currentDate.plusDays(1);
        }

        // Lưu tất cả chấm công
        List<Attendance> savedAttendances = attendanceRepository.saveAll(attendances);
        log.debug("autoGenerateAttendances completed: createdCount={}", savedAttendances.size());

        return savedAttendances.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public AttendanceResponse getAttendanceById(Long id) {
        log.debug("getAttendanceById requested: id={}", id);
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_NOT_FOUND));
        return mapToResponse(attendance);
    }

    @Override
    public List<AttendanceResponse> getAllAttendances() {
        log.debug("getAllAttendances requested");
        return attendanceRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<AttendanceResponse> getAttendancesWithFilter(String keyword, Integer month, Integer year, int page, int pageSize) {
        log.debug("getAttendancesWithFilter requested: keyword='{}', month={}, year={}, page={}, pageSize={}", keyword, month, year, page, pageSize);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("date").descending());
        Page<Attendance> attendancePage = attendanceRepository.findByFilters(keyword, month, year, pageable);

        List<AttendanceResponse> attendances = attendancePage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PageResponse.<AttendanceResponse>builder()
                .content(attendances)
                .page(attendancePage.getNumber())
                .pageSize(attendancePage.getSize())
                .totalElements(attendancePage.getTotalElements())
                .totalPages(attendancePage.getTotalPages())
                .first(attendancePage.isFirst())
                .last(attendancePage.isLast())
                .build();
    }

    @Override
    public PageResponse<AttendanceResponse> getAttendancesByEmployee(Long employeeId, Integer month, Integer year, int page, int pageSize) {
        // Validate employee exists
        employeeRepository.findById(employeeId)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));
                log.debug("getAttendancesByEmployee requested: employeeId={}, month={}, year={}, page={}, pageSize={}", employeeId, month, year, page, pageSize);

        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("date").descending());
        Page<Attendance> attendancePage = attendanceRepository.findByEmployeeAndFilters(employeeId, month, year, pageable);

        List<AttendanceResponse> attendances = attendancePage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PageResponse.<AttendanceResponse>builder()
                .content(attendances)
                .page(attendancePage.getNumber())
                .pageSize(attendancePage.getSize())
                .totalElements(attendancePage.getTotalElements())
                .totalPages(attendancePage.getTotalPages())
                .first(attendancePage.isFirst())
                .last(attendancePage.isLast())
                .build();
    }

    @Override
    public AttendanceResponse updateAttendance(Long id, AttendanceRequest request) {
                String username = "anonymous";
                try {
                        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                                        .getContext().getAuthentication();
                        if (auth != null && auth.getName() != null) username = auth.getName();
                } catch (Exception ignored) {
                }
        log.debug("updateAttendance requested by {}: id={}, employeeId={}, date={}", username, id, request.getEmployeeId(), request.getDate());
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_NOT_FOUND));

                // Kiểm tra nếu đổi ngày, phải kiểm tra trùng (repository now checks assignment.employee)
                if (!attendance.getDate().equals(request.getDate())) {
                        attendanceRepository.findByEmployeeAndDate(request.getEmployeeId(), request.getDate())
                                        .ifPresent(a -> {
                                                throw new AppException(ErrorCode.ATTENDANCE_ALREADY_EXISTS);
                                        });
                }

                Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

                // Validate assignment's employee matches requested employeeId
                if (assignment.getEmployee() == null || !assignment.getEmployee().getId().equals(request.getEmployeeId())) {
                        throw new AppException(ErrorCode.EMPLOYEE_NOT_FOUND);
                }

        User approver = null;
        if (request.getApprovedBy() != null) {
            approver = userRepository.findById(request.getApprovedBy())
                    .orElse(null);
        }

        attendance.setAssignment(assignment);
        attendance.setDate(request.getDate());
        attendance.setWorkHours(request.getWorkHours());
        attendance.setBonus(request.getBonus());
        attendance.setPenalty(request.getPenalty());
        attendance.setSupportCost(request.getSupportCost());
        attendance.setIsOvertime(request.getIsOvertime());
        attendance.setOvertimeAmount(request.getOvertimeAmount());
        attendance.setApprovedBy(approver);
        attendance.setDescription(request.getDescription());
        attendance.setUpdatedAt(LocalDateTime.now());

        Attendance updatedAttendance = attendanceRepository.save(attendance);
        log.debug("updateAttendance completed by {}: id={}", username, updatedAttendance.getId());
        return mapToResponse(updatedAttendance);
    }

    @Override
    public void deleteAttendance(Long id) {
                String username = "anonymous";
                try {
                        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                                        .getContext().getAuthentication();
                        if (auth != null && auth.getName() != null) username = auth.getName();
                } catch (Exception ignored) {
                }
        log.debug("deleteAttendance requested by {}: id={}", username, id);
        if (!attendanceRepository.existsById(id)) {
            throw new AppException(ErrorCode.ATTENDANCE_NOT_FOUND);
        }
        attendanceRepository.deleteById(id);
        log.debug("deleteAttendance completed by {}: id={}", username, id);
    }

        private AttendanceResponse mapToResponse(Attendance attendance) {
                Assignment assignment = attendance.getAssignment();
                Employee employee = assignment != null ? assignment.getEmployee() : null;
                User approver = attendance.getApprovedBy();

        return AttendanceResponse.builder()
                .id(attendance.getId())
                .employeeId(employee != null ? employee.getId() : null)
                .employeeName(employee != null ? employee.getName() : null)
                .employeeCode(employee != null ? employee.getEmployeeCode() : null)
                .assignmentId(assignment.getId())
                .assignmentType(assignment.getAssignmentType() != null ? assignment.getAssignmentType().name() : null)
                .customerId(assignment.getContract().getCustomer().getId())
                .customerName(assignment.getContract().getCustomer().getName())
                .date(attendance.getDate())
                .workHours(attendance.getWorkHours())
                .bonus(attendance.getBonus())
                .penalty(attendance.getPenalty())
                .supportCost(attendance.getSupportCost())
                .isOvertime(attendance.getIsOvertime())
                .overtimeAmount(attendance.getOvertimeAmount())
                .approvedBy(approver != null ? approver.getId() : null)
                .approvedByName(approver != null ? approver.getUsername() : null)
                .description(attendance.getDescription())
                .createdAt(attendance.getCreatedAt())
                .updatedAt(attendance.getUpdatedAt())
                .build();
    }

    @Override
    public TotalDaysResponse getTotalDaysByEmployee(Long employeeId, Integer month, Integer year) {
        log.debug("getTotalDaysByEmployee requested: employeeId={}, month={}, year={}", employeeId, month, year);
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate startDate = yearMonth.atDay(1);
        LocalDate endDate = yearMonth.atEndOfMonth();

        List<Attendance> attendances = attendanceRepository.findByEmployeeAndDateBetween(
                employeeId, startDate, endDate);

        int totalDays = attendances.size();

        return TotalDaysResponse.builder()
                .employeeId(employee.getId())
                .employeeName(employee.getName())
                .employeeCode(employee.getEmployeeCode())
                .month(month)
                .year(year)
                .totalDays(totalDays)
                .message(String.format("Nhân viên %s có %d ngày công trong tháng %d/%d",
                        employee.getName(), totalDays, month, year))
                .build();
    }
}
