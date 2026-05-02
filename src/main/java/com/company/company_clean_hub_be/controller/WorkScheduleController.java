package com.company.company_clean_hub_be.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.company_clean_hub_be.dto.request.WorkScheduleCaptureRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.WorkScheduleContractSummary;
import com.company.company_clean_hub_be.dto.response.WorkScheduleResponse;
import com.company.company_clean_hub_be.dto.response.VerificationImageResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.service.WorkScheduleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/work-schedules")
public class WorkScheduleController {

    private final WorkScheduleService workScheduleService;
    private final com.company.company_clean_hub_be.repository.AssignmentRepository assignmentRepository;

    @GetMapping("/assignment/{assignmentId}")
    public ApiResponse<List<WorkScheduleResponse>> getByAssignment(@PathVariable Long assignmentId) {
        log.info("Getting work schedules for assignment: {}", assignmentId);
        List<WorkScheduleResponse> schedules = workScheduleService.getWorkSchedulesByAssignment(assignmentId);
        return ApiResponse.success("Lấy lịch làm việc thành công", schedules, HttpStatus.OK.value());
    }

    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<WorkScheduleResponse>> getByEmployee(
            @PathVariable Long employeeId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        
        log.info("Getting work schedules for employee: {} from {} to {}", employeeId, startDate, endDate);
        List<WorkScheduleResponse> schedules = workScheduleService.getWorkSchedulesByEmployee(employeeId, startDate, endDate);
        return ApiResponse.success("Lấy lịch làm việc thành công", schedules, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    public ApiResponse<WorkScheduleResponse> getById(@PathVariable Long id) {
        log.info("Getting work schedule: {}", id);
        WorkScheduleResponse schedule = workScheduleService.getWorkScheduleById(id);
        return ApiResponse.success("Lấy lịch làm việc thành công", schedule, HttpStatus.OK.value());
    }

    @PostMapping("/capture")
    public ApiResponse<WorkScheduleResponse> capturePhoto(
            @Valid @RequestBody WorkScheduleCaptureRequest request,
            Principal principal) {
        
        log.info("Capturing photo for work schedule: {} by user: {}", 
            request.getWorkScheduleId(), principal.getName());
        
        WorkScheduleResponse schedule = workScheduleService.capturePhoto(request);
        return ApiResponse.success("Chụp ảnh chấm công thành công", schedule, HttpStatus.OK.value());
    }

    @GetMapping("/{id}/can-capture")
    public ApiResponse<Boolean> canCapturePhoto(@PathVariable Long id) {
        boolean canCapture = workScheduleService.canCapturePhoto(id);
        return ApiResponse.success("Kiểm tra quyền chụp ảnh thành công", canCapture, HttpStatus.OK.value());
    }

    @PutMapping("/{id}/cancel")
    public ApiResponse<WorkScheduleResponse> cancel(
            @PathVariable Long id,
            @RequestParam String reason,
            Principal principal) {
        
        log.info("Cancelling work schedule: {} by user: {}", id, principal.getName());
        WorkScheduleResponse schedule = workScheduleService.cancelWorkSchedule(id, reason);
        return ApiResponse.success("Hủy lịch làm việc thành công", schedule, HttpStatus.OK.value());
    }

    @PostMapping("/{id}/create-attendance")
    public ApiResponse<WorkScheduleResponse> createAttendanceForMissed(
            @PathVariable Long id,
            @RequestParam String reason,
            Principal principal) {
        
        log.info("Creating attendance for missed schedule: {} by user: {}", id, principal.getName());
        WorkScheduleResponse schedule = workScheduleService.createAttendanceForMissed(id, reason);
        return ApiResponse.success("Tạo điểm danh cho ngày nghỉ thành công", schedule, HttpStatus.OK.value());
    }
    
    @GetMapping("/missed")
    public ApiResponse<List<WorkScheduleResponse>> getMissedSchedules(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        
        log.info("[QUERY-DEBUG] ===== getMissedSchedules called =====");
        log.info("[QUERY-DEBUG] Parameters: month={}, year={}", month, year);
        
        LocalDate startDate;
        LocalDate endDate;
        
        if (month != null && year != null) {
            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        } else {
            // Default to current month
            startDate = LocalDate.now().withDayOfMonth(1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        }
        
        log.info("[QUERY-DEBUG] Query range: {} to {}", startDate, endDate);
        List<WorkScheduleResponse> schedules = workScheduleService.getMissedSchedules(startDate, endDate);
        log.info("[QUERY-DEBUG] Found {} missed schedules", schedules.size());
        schedules.forEach(ws -> log.info("[QUERY-DEBUG]   - Schedule: assignmentId={}, employeeId={}, date={}, status={}, reason={}", 
                ws.getAssignmentId(), ws.getEmployeeId(), ws.getScheduledDate(), ws.getStatus(), ws.getReason()));
        
        return ApiResponse.success("Lấy danh sách lịch bị bỏ lỡ thành công", schedules, HttpStatus.OK.value());
    }
    
    @GetMapping("/missed/employee/{employeeId}")
    public ApiResponse<List<WorkScheduleResponse>> getMissedSchedulesByEmployee(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        
        log.info("Getting missed schedules for employee: {}, month: {}, year: {}", employeeId, month, year);
        
        LocalDate startDate;
        LocalDate endDate;
        
        if (month != null && year != null) {
            startDate = LocalDate.of(year, month, 1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        } else {
            // Default to current month
            startDate = LocalDate.now().withDayOfMonth(1);
            endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());
        }
        
        List<WorkScheduleResponse> schedules = workScheduleService.getMissedSchedulesByEmployee(employeeId, startDate, endDate);
        return ApiResponse.success("Lấy danh sách lịch bị bỏ lỡ thành công", schedules, HttpStatus.OK.value());
    }
    
    @GetMapping("/by-date-range")
    public ApiResponse<List<WorkScheduleResponse>> getByDateRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) String status) {
        
        log.info("[QUERY-DEBUG] ===== getByDateRange called =====");
        log.info("[QUERY-DEBUG] Parameters: startDate={}, endDate={}, employeeId={}, status={}", 
            startDate, endDate, employeeId, status);
        
        List<WorkScheduleResponse> schedules = workScheduleService.getWorkSchedulesByDateRange(startDate, endDate, employeeId, status);
        log.info("[QUERY-DEBUG] Found {} schedules", schedules.size());
        
        // Log chi tiết cho employee 233 để debug
        List<WorkScheduleResponse> employee233Schedules = schedules.stream()
                .filter(ws -> ws.getEmployeeId() == 233)
                .collect(Collectors.toList());
        if (!employee233Schedules.isEmpty()) {
                log.info("[QUERY-DEBUG] ===== Employee 233 schedules (count={}): =====", employee233Schedules.size());
                employee233Schedules.forEach(ws -> log.info("[QUERY-DEBUG]   - assignmentId={}, contractId={}, date={}, status={}, reason={}", 
                        ws.getAssignmentId(), ws.getContractId(), ws.getScheduledDate(), ws.getStatus(), ws.getReason()));
                
                // Kiểm tra assignment 727 details
                if (employee233Schedules.stream().anyMatch(ws -> ws.getAssignmentId() == 727)) {
                        log.info("[QUERY-DEBUG] ===== CHECKING ASSIGNMENT 727 =====");
                        try {
                                Assignment a727 = assignmentRepository.findById(727L).orElse(null);
                                if (a727 != null) {
                                        log.info("[QUERY-DEBUG] Assignment 727: startDate={}, endDate={}, status={}, contractId={}, workDays={}, plannedDays={}",
                                                a727.getStartDate(), a727.getEndDate(), a727.getStatus(), 
                                                a727.getContract() != null ? a727.getContract().getId() : null,
                                                a727.getWorkDays(), a727.getPlannedDays());
                                } else {
                                        log.warn("[QUERY-DEBUG] Assignment 727 NOT FOUND in database!");
                                }
                        } catch (Exception e) {
                                log.error("[QUERY-DEBUG] Error checking assignment 727: {}", e.getMessage());
                        }
                }
        }
        
        return ApiResponse.success("Lấy lịch làm việc thành công", schedules, HttpStatus.OK.value());
    }
    
    @GetMapping("/by-date")
    public ApiResponse<List<WorkScheduleResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status) {
        
        log.info("[QUERY-DEBUG] ===== getByDate called =====");
        log.info("[QUERY-DEBUG] Parameters: date={}, status={}", date, status);
        
        List<WorkScheduleResponse> schedules = workScheduleService.getWorkSchedulesByDate(date, status);
        log.info("[QUERY-DEBUG] Found {} schedules", schedules.size());
        schedules.forEach(ws -> log.info("[QUERY-DEBUG]   - Schedule: assignmentId={}, employeeId={}, date={}, status={}, reason={}", 
                ws.getAssignmentId(), ws.getEmployeeId(), ws.getScheduledDate(), ws.getStatus(), ws.getReason()));
        
        return ApiResponse.success("Lấy lịch làm việc thành công", schedules, HttpStatus.OK.value());
    }
    
    @GetMapping("/stats")
    public ApiResponse<com.company.company_clean_hub_be.dto.response.WorkScheduleStatsResponse> getStats(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long employeeId) {
        
        log.info("Getting work schedule stats: month={}, year={}, employeeId={}", month, year, employeeId);
        
        com.company.company_clean_hub_be.dto.response.WorkScheduleStatsResponse stats = 
            workScheduleService.getStats(month, year, employeeId);
        return ApiResponse.success("Lấy thống kê thành công", stats, HttpStatus.OK.value());
    }
    
    @GetMapping("/employees-with-schedules")
    public ApiResponse<List<com.company.company_clean_hub_be.dto.response.EmployeeScheduleSummary>> getEmployeesWithSchedules(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        
        log.info("Getting employees with schedules: month={}, year={}", month, year);
        
        List<com.company.company_clean_hub_be.dto.response.EmployeeScheduleSummary> employees = 
            workScheduleService.getEmployeesWithSchedules(month, year);
        return ApiResponse.success("Lấy danh sách nhân viên thành công", employees, HttpStatus.OK.value());
    }

    // Lấy ảnh của 1 work_schedule cụ thể
    @GetMapping("/{id}/image")
    public ApiResponse<VerificationImageResponse> getImageByWorkSchedule(@PathVariable Long id) {
        log.info("Getting image for work schedule: {}", id);
        VerificationImageResponse image = workScheduleService.getImageByWorkScheduleId(id);
        return ApiResponse.success("Lấy ảnh thành công", image, HttpStatus.OK.value());
    }

    // Lấy danh sách hợp đồng có work_schedules (cho trang quản lý)
    @GetMapping("/contracts-summary")
    public ApiResponse<List<WorkScheduleContractSummary>> getContractsSummary(
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) String sort) {
        log.info("Getting contracts summary: month={}, year={}, sort={}", month, year, sort);
        List<WorkScheduleContractSummary> summary = workScheduleService.getContractsSummary(month, year, sort);
        return ApiResponse.success("Lấy danh sách hợp đồng thành công", summary, HttpStatus.OK.value());
    }
}
