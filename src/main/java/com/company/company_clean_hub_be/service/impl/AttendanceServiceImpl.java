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
import com.company.company_clean_hub_be.dto.request.AttendanceCaptureRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.ArrayList;
import java.util.List;
import com.company.company_clean_hub_be.exception.ResourceNotFoundException;
import com.company.company_clean_hub_be.service.AttendanceService;
import com.company.company_clean_hub_be.service.FileStorageService;
import com.company.company_clean_hub_be.service.VerificationService;
import com.company.company_clean_hub_be.repository.AssignmentVerificationRepository;
import com.company.company_clean_hub_be.entity.AssignmentVerification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
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
    private final FileStorageService fileStorageService;
    private final VerificationService verificationService;
    private final AssignmentVerificationRepository assignmentVerificationRepository;

    @Override
    public AttendanceResponse createAttendance(AttendanceRequest request) {
        String username = "anonymous";
        try {
                org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                        .getContext().getAuthentication();
                if (auth != null && auth.getName() != null) username = auth.getName();
        } catch (Exception ignored) {
        }
        log.info("createAttendance requested by {}: employeeId={}, assignmentId={}, date={}", username, request.getEmployeeId(), request.getAssignmentId(), request.getDate());
                // Kiểm tra nhân viên đã chấm công cho cùng assignment vào ngày này chưa
                attendanceRepository.findByAssignmentAndEmployeeAndDate(request.getAssignmentId(), request.getEmployeeId(), request.getDate())
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
                .deleted(false)
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Attendance savedAttendance = attendanceRepository.save(attendance);
        log.info("createAttendance completed by {}: attendanceId={}", username, savedAttendance.getId());
        return mapToResponse(savedAttendance);
    }

    @Override
    public List<AttendanceResponse> autoGenerateAttendances(AutoAttendanceRequest request) {
        log.info("autoGenerateAttendances requested: employeeId={}, assignmentId={}, month={}, year={}", request.getEmployeeId(), request.getAssignmentId(), request.getMonth(), request.getYear());
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
            
            // Kiểm tra đã có chấm công cho cùng assignment vào ngày này chưa
            boolean alreadyExists = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                    request.getAssignmentId(),
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
                                                .deleted(false)
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
        log.info("autoGenerateAttendances completed: createdCount={}", savedAttendances.size());

        return savedAttendances.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public AttendanceResponse getAttendanceById(Long id) {
        log.info("getAttendanceById requested: id={}", id);
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_NOT_FOUND));
                if (attendance.getDeleted() != null && attendance.getDeleted()) {
                        throw new AppException(ErrorCode.ATTENDANCE_NOT_FOUND);
                }
        return mapToResponse(attendance);
    }

    @Override
    public List<AttendanceResponse> getAllAttendances() {
        log.info("getAllAttendances requested");
                return attendanceRepository.findAllActive().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<AttendanceResponse> getAttendancesWithFilter(String keyword, Integer month, Integer year, int page, int pageSize) {
        log.info("getAttendancesWithFilter requested: keyword='{}', month={}, year={}, page={}, pageSize={}", keyword, month, year, page, pageSize);
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
                log.info("getAttendancesByEmployee requested: employeeId={}, month={}, year={}, page={}, pageSize={}", employeeId, month, year, page, pageSize);

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
        log.info("updateAttendance requested by {}: id={}, employeeId={}, date={}", username, id, request.getEmployeeId(), request.getDate());
        Attendance attendance = attendanceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_NOT_FOUND));

                // Kiểm tra nếu đổi ngày, phải kiểm tra trùng (repository now checks assignment.employee)
                if (!attendance.getDate().equals(request.getDate())) {
                        attendanceRepository.findByAssignmentAndEmployeeAndDate(request.getAssignmentId(), request.getEmployeeId(), request.getDate())
                                        .ifPresent(a -> {
                                                if (!a.getId().equals(id)) {
                                                    throw new AppException(ErrorCode.ATTENDANCE_ALREADY_EXISTS);
                                                }
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
        attendance.setDeleted(attendance.getDeleted());
        attendance.setDescription(request.getDescription());
        attendance.setUpdatedAt(LocalDateTime.now());

        Attendance updatedAttendance = attendanceRepository.save(attendance);
        log.info("updateAttendance completed by {}: id={}", username, updatedAttendance.getId());
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
        log.info("deleteAttendance requested by {}: id={}", username, id);
        if (!attendanceRepository.existsById(id)) {
            throw new AppException(ErrorCode.ATTENDANCE_NOT_FOUND);
        }
                Attendance attendance = attendanceRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_NOT_FOUND));
                attendance.setDeleted(true);
                attendance.setDeletedAt(LocalDateTime.now());
                attendance.setUpdatedAt(LocalDateTime.now());
                attendanceRepository.save(attendance);
                log.info("soft-delete attendance completed by {}: id={}", username, id);
    }

        @Override
        public void softDeleteAttendance(com.company.company_clean_hub_be.dto.request.AttendanceDeleteRequest request) {
                String username = "anonymous";
                try {
                        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                                        .getContext().getAuthentication();
                        if (auth != null && auth.getName() != null) username = auth.getName();
                } catch (Exception ignored) {
                }
                LocalDate date = request.getDate();
                Long contractId = request.getContractId();
                Long employeeId = request.getEmployeeId();
                String description = request.getDescription();

                log.info("softDeleteAttendance requested by {}: date={}, contractId={}, employeeId={}", username, date, contractId, employeeId);

                Attendance attendance = attendanceRepository.findByContractAndEmployeeAndDate(contractId, employeeId, date)
                                .orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_NOT_FOUND));

                attendance.setDeleted(true);
                attendance.setDeletedAt(LocalDateTime.now());
                if (description != null && !description.isBlank()) {
                        attendance.setDescription(description);
                }
                attendance.setUpdatedAt(LocalDateTime.now());
                attendanceRepository.save(attendance);

                // Tái tính workDays của assignment
                Assignment assignment = attendance.getAssignment();
                if (assignment != null) {
                        int month = date.getMonthValue();
                        int year = date.getYear();
                        recalculateAssignmentWorkDays(assignment, month, year);
                }

                log.info("softDeleteAttendance completed by {}: attendanceId={}", username, attendance.getId());
        }

        @Override
        public void restoreAttendance(Long id) {
                String username = "anonymous";
                try {
                        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                                        .getContext().getAuthentication();
                        if (auth != null && auth.getName() != null) username = auth.getName();
                } catch (Exception ignored) {
                }
                log.info("restoreAttendance requested by {}: id={}", username, id);

                Attendance attendance = attendanceRepository.findById(id)
                                .orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_NOT_FOUND));

                if (attendance.getDeleted() == null || !attendance.getDeleted()) {
                        throw new AppException(ErrorCode.ATTENDANCE_NOT_FOUND);
                }

                attendance.setDeleted(false);
                attendance.setDeletedAt(null);
                attendance.setUpdatedAt(LocalDateTime.now());
                attendanceRepository.save(attendance);

                // Tái tính workDays của assignment
                Assignment assignment = attendance.getAssignment();
                if (assignment != null && attendance.getDate() != null) {
                        int month = attendance.getDate().getMonthValue();
                        int year = attendance.getDate().getYear();
                        recalculateAssignmentWorkDays(assignment, month, year);
                }

                log.info("restoreAttendance completed by {}: id={}", username, id);
        }

        @Override
        public void restoreByDateContractEmployee(com.company.company_clean_hub_be.dto.request.AttendanceRestoreRequest request) {
                String username = "anonymous";
                try {
                        org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                                        .getContext().getAuthentication();
                        if (auth != null && auth.getName() != null) username = auth.getName();
                } catch (Exception ignored) {
                }
                LocalDate date = request.getDate();
                Long contractId = request.getContractId();
                Long employeeId = request.getEmployeeId();
                log.info("restoreByDateContractEmployee requested by {}: date={}, contractId={}, employeeId={}", username, date, contractId, employeeId);

                Attendance attendance = attendanceRepository.findByContractAndEmployeeAndDate(contractId, employeeId, date)
                                .orElseThrow(() -> new AppException(ErrorCode.ATTENDANCE_NOT_FOUND));

                if (attendance.getDeleted() == null || !attendance.getDeleted()) {
                        throw new AppException(ErrorCode.ATTENDANCE_NOT_FOUND);
                }

                attendance.setDeleted(false);
                attendance.setDeletedAt(null);
                attendance.setUpdatedAt(LocalDateTime.now());
                attendanceRepository.save(attendance);

                // Tái tính workDays của assignment
                Assignment assignment = attendance.getAssignment();
                if (assignment != null) {
                        int month = date.getMonthValue();
                        int year = date.getYear();
                        recalculateAssignmentWorkDays(assignment, month, year);
                }

                log.info("restoreByDateContractEmployee completed by {}: attendanceId={}", username, attendance.getId());
        }

    @Override
    public List<AttendanceResponse> getTodayAttendancesByEmployee(Long employeeId) {
        log.info("getTodayAttendancesByEmployee requested: employeeId={}", employeeId);
        LocalDate today = LocalDate.now();
        List<Attendance> attendances = attendanceRepository.findAllByEmployeeAndDate(employeeId, today);
        return attendances.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    // DEPRECATED: Use VerificationService.captureVerificationImage() instead
    // This method is kept for backward compatibility but should not be used
    @Override
    @Transactional
    public AttendanceResponse captureAttendance(AttendanceCaptureRequest request) throws IOException {
        log.warn("DEPRECATED: captureAttendance called. Use VerificationService.captureVerificationImage() instead");
        throw new AppException(ErrorCode.METHOD_DEPRECATED);
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
                .assignmentId(assignment != null ? assignment.getId() : null)
                .assignmentType(assignment != null && assignment.getAssignmentType() != null ? assignment.getAssignmentType().name() : null)
                .customerId(assignment != null && assignment.getContract() != null && assignment.getContract().getCustomer() != null ? assignment.getContract().getCustomer().getId() : null)
                .customerName(assignment != null && assignment.getContract() != null && assignment.getContract().getCustomer() != null ? assignment.getContract().getCustomer().getName() : null)
                .date(attendance.getDate())
                .workHours(attendance.getWorkHours())
                .bonus(attendance.getBonus())
                .penalty(attendance.getPenalty())
                .supportCost(attendance.getSupportCost())
                .isOvertime(attendance.getIsOvertime())
                .overtimeAmount(attendance.getOvertimeAmount())
                .approvedBy(approver != null ? approver.getId() : null)
                .approvedByName(approver != null ? approver.getUsername() : null)
                .evaluationStatus(attendance.getEvaluation() != null ? attendance.getEvaluation().getStatus().name() : null)
                // Image-related fields removed - now stored in VerificationImage
                .description(attendance.getDescription())
                .createdAt(attendance.getCreatedAt())
                .updatedAt(attendance.getUpdatedAt())
                .build();
    }

    @Override
    public TotalDaysResponse getTotalDaysByEmployee(Long employeeId, Integer month, Integer year) {
        log.info("getTotalDaysByEmployee requested: employeeId={}, month={}, year={}", employeeId, month, year);
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

        @Override
        public java.util.Map<Long, java.util.Map<Long, java.util.List<AttendanceResponse>>> getAttendancesGroupedByContractAndEmployee(Integer month, Integer year) {
                log.info("getAttendancesGroupedByContractAndEmployee requested: month={}, year={}", month, year);

                // Single query to fetch attendances for the month/year ordered by contract, employee, date
                List<Attendance> attendances = attendanceRepository.findByMonthYearOrderByContractEmployeeDate(month, year);

                java.util.Map<Long, java.util.Map<Long, java.util.List<AttendanceResponse>>> result = new java.util.LinkedHashMap<>();

                for (Attendance a : attendances) {
                        if (a.getAssignment() == null || a.getAssignment().getContract() == null || a.getEmployee() == null) continue;
                        Long contractId = a.getAssignment().getContract().getId();
                        Long employeeId = a.getEmployee().getId();

                        result.computeIfAbsent(contractId, k -> new java.util.LinkedHashMap<>())
                                        .computeIfAbsent(employeeId, k -> new java.util.ArrayList<>())
                                        .add(mapToResponse(a));
                }

                log.info("getAttendancesGroupedByContractAndEmployee completed: contracts={}, totalAttendances={}", result.size(), attendances.size());
                return result;
        }

        @Override
        public java.util.Map<Long, java.util.List<AttendanceResponse>> getAttendancesByContractGroupedByEmployee(Long contractId, Integer month, Integer year) {
                log.info("getAttendancesByContractGroupedByEmployee requested: contractId={}, month={}, year={}", contractId, month, year);

                List<Attendance> attendances = attendanceRepository.findByContractAndMonthYearOrderByEmployeeDate(contractId, month, year);

                java.util.Map<Long, java.util.List<AttendanceResponse>> result = new java.util.LinkedHashMap<>();

                for (Attendance a : attendances) {
                        if (a.getEmployee() == null) continue;
                        Long employeeId = a.getEmployee().getId();
                        result.computeIfAbsent(employeeId, k -> new java.util.ArrayList<>()).add(mapToResponse(a));
                }

                log.info("getAttendancesByContractGroupedByEmployee completed: contractId={}, employees={}", contractId, result.size());
                return result;
        }

        @Override
        public PageResponse<AttendanceResponse> getDeletedAttendances(Long contractId, Long employeeId, Integer month, Integer year, int page, int pageSize) {
                log.info("getDeletedAttendances requested: contractId={}, employeeId={}, month={}, year={}, page={}, pageSize={}", contractId, employeeId, month, year, page, pageSize);
                Pageable pageable = PageRequest.of(page, pageSize, Sort.by("date").descending());
                org.springframework.data.domain.Page<Attendance> attendancePage = attendanceRepository.findDeletedByFilters(contractId, employeeId, month, year, pageable);

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

        private void recalculateAssignmentWorkDays(Assignment assignment, int month, int year) {
                Long count = attendanceRepository.countActiveAttendancesByAssignmentAndMonthYear(
                                assignment.getId(), month, year);
                assignment.setWorkDays(count != null ? count.intValue() : 0);
                assignmentRepository.save(assignment);
                log.info("Recalculated workDays for assignmentId={}: workDays={}", assignment.getId(), assignment.getWorkDays());
        }

    @Override
    @Transactional
    public List<AttendanceResponse> autoGenerateAttendancesWithVerification(AutoAttendanceRequest request) {
        log.info("autoGenerateAttendancesWithVerification requested: employeeId={}, assignmentId={}, month={}, year={}", 
            request.getEmployeeId(), request.getAssignmentId(), request.getMonth(), request.getYear());
        
        Employee employee = employeeRepository.findById(request.getEmployeeId())
                .orElseThrow(() -> new AppException(ErrorCode.EMPLOYEE_NOT_FOUND));

        Assignment assignment = assignmentRepository.findById(request.getAssignmentId())
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        // Check if assignment requires verification
        boolean requiresVerification = verificationService.requiresVerification(assignment);
        
        if (!requiresVerification) {
            // No verification needed, generate all attendances normally
            return autoGenerateAttendances(request);
        }

        // Verification required - only generate first day
        YearMonth yearMonth = YearMonth.of(request.getYear(), request.getMonth());
        LocalDate startDate = yearMonth.atDay(1);
        
        List<LocalDate> excludeDates = request.getExcludeDates() != null ? 
                request.getExcludeDates() : new ArrayList<>();

        // Find first working day
        LocalDate firstWorkingDay = findFirstWorkingDay(startDate, yearMonth.atEndOfMonth(), excludeDates);
        
        if (firstWorkingDay == null) {
            log.warn("No working days found in month {}/{}", request.getMonth(), request.getYear());
            return new ArrayList<>();
        }

        // Generate only first day attendance
        List<Attendance> attendances = new ArrayList<>();
        
        boolean alreadyExists = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                request.getAssignmentId(),
                request.getEmployeeId(), 
                firstWorkingDay
        ).isPresent();

        if (!alreadyExists) {
            // Get or create verification requirement
            AssignmentVerification verification = getOrCreateVerification(assignment);
            
            Attendance attendance = Attendance.builder()
                    .assignment(assignment)
                    .employee(employee)
                    .date(firstWorkingDay)
                    .workHours(java.math.BigDecimal.valueOf(8))
                    .bonus(java.math.BigDecimal.ZERO)
                    .penalty(java.math.BigDecimal.ZERO)
                    .supportCost(java.math.BigDecimal.ZERO)
                    .isOvertime(false)
                    .deleted(false)
                    .overtimeAmount(java.math.BigDecimal.ZERO)
                    .assignmentVerification(verification)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            
            attendances.add(attendance);
        }

        List<Attendance> savedAttendances = attendanceRepository.saveAll(attendances);
        log.info("autoGenerateAttendancesWithVerification completed: createdCount={} (verification required)", savedAttendances.size());

        return savedAttendances.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }
    @Override
    @Transactional
    public AttendanceResponse generateSingleAttendance(Long assignmentId, LocalDate date) {
        log.info("generateSingleAttendance: assignmentId={}, date={}", assignmentId, date);
        
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        // Check if attendance already exists
        Optional<Attendance> existing = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                assignmentId, assignment.getEmployee().getId(), date);
        
        if (existing.isPresent()) {
            log.warn("Attendance already exists for assignment {} on date {}", assignmentId, date);
            return mapToResponse(existing.get());
        }

        // Get verification if exists
        AssignmentVerification verification = verificationService.getVerificationByAssignmentId(assignmentId)
                .map(response -> assignmentVerificationRepository.findById(response.getId()).orElse(null))
                .orElse(null);

        Attendance attendance = Attendance.builder()
                .assignment(assignment)
                .employee(assignment.getEmployee())
                .date(date)
                .workHours(java.math.BigDecimal.valueOf(8))
                .bonus(java.math.BigDecimal.ZERO)
                .penalty(java.math.BigDecimal.ZERO)
                .supportCost(java.math.BigDecimal.ZERO)
                .isOvertime(false)
                .deleted(false)
                .overtimeAmount(java.math.BigDecimal.ZERO)
                .assignmentVerification(verification)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Attendance saved = attendanceRepository.save(attendance);
        log.info("Generated single attendance: {}", saved.getId());
        
        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void generateRemainingAttendances(Long assignmentId, LocalDate fromDate) {
        log.info("generateRemainingAttendances: assignmentId={}, fromDate={}", assignmentId, fromDate);
        
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));

        // Generate attendances from fromDate to end of month
        LocalDate endDate = fromDate.withDayOfMonth(fromDate.lengthOfMonth());
        LocalDate currentDate = fromDate.plusDays(1); // Start from next day
        
        List<Attendance> attendances = new ArrayList<>();
        
        while (!currentDate.isAfter(endDate)) {
            // Skip Sundays
            if (currentDate.getDayOfWeek() != DayOfWeek.SUNDAY) {
                // Check if attendance already exists
                boolean alreadyExists = attendanceRepository.findByAssignmentAndEmployeeAndDate(
                        assignmentId, assignment.getEmployee().getId(), currentDate).isPresent();
                
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
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    
                    attendances.add(attendance);
                }
            }
            currentDate = currentDate.plusDays(1);
        }

        if (!attendances.isEmpty()) {
            attendanceRepository.saveAll(attendances);
            log.info("Generated {} remaining attendances for assignment {}", attendances.size(), assignmentId);
        }
    }
    private LocalDate findFirstWorkingDay(LocalDate startDate, LocalDate endDate, List<LocalDate> excludeDates) {
        LocalDate currentDate = startDate;
        while (!currentDate.isAfter(endDate)) {
            boolean isSunday = currentDate.getDayOfWeek() == DayOfWeek.SUNDAY;
            boolean isExcluded = excludeDates.contains(currentDate);
            
            if (!isSunday && !isExcluded) {
                return currentDate;
            }
            currentDate = currentDate.plusDays(1);
        }
        return null;
    }

    private AssignmentVerification getOrCreateVerification(Assignment assignment) {
        return verificationService.getVerificationByAssignmentId(assignment.getId())
                .map(response -> assignmentVerificationRepository.findById(response.getId()).orElse(null))
                .orElseGet(() -> {
                    String reason = verificationService.isEmployeeNew(assignment.getEmployee().getId()) 
                            ? "NEW_EMPLOYEE" : "CONTRACT_SETTING";
                    return verificationService.createVerificationRequirement(assignment, reason);
                });
    }
}