package com.company.company_clean_hub_be.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

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
        
        log.info("Getting missed schedules for month: {}, year: {}", month, year);
        
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
        
        List<WorkScheduleResponse> schedules = workScheduleService.getMissedSchedules(startDate, endDate);
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
        
        log.info("Getting work schedules by date range: {} to {}, employeeId: {}, status: {}", 
            startDate, endDate, employeeId, status);
        
        List<WorkScheduleResponse> schedules = workScheduleService.getWorkSchedulesByDateRange(startDate, endDate, employeeId, status);
        return ApiResponse.success("Lấy lịch làm việc thành công", schedules, HttpStatus.OK.value());
    }
    
    @GetMapping("/by-date")
    public ApiResponse<List<WorkScheduleResponse>> getByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String status) {
        
        log.info("Getting work schedules by date: {}, status: {}", date, status);
        
        List<WorkScheduleResponse> schedules = workScheduleService.getWorkSchedulesByDate(date, status);
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
