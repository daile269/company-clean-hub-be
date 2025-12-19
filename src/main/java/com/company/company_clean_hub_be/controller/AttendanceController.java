package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.AttendanceRequest;
import com.company.company_clean_hub_be.dto.request.AutoAttendanceRequest;
import com.company.company_clean_hub_be.dto.request.TemporaryAssignmentRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.AttendanceResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.TemporaryAssignmentResponse;
import com.company.company_clean_hub_be.dto.response.TotalDaysResponse;
import com.company.company_clean_hub_be.service.AttendanceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/attendances")
public class AttendanceController {
    private final AttendanceService attendanceService;

    @PostMapping
    public ApiResponse<AttendanceResponse> createAttendance(@Valid @RequestBody AttendanceRequest request) {
        AttendanceResponse attendance = attendanceService.createAttendance(request);
        return ApiResponse.success("Tạo chấm công thành công", attendance, HttpStatus.CREATED.value());
    }

    @PostMapping("/auto-generate")
    public ApiResponse<List<AttendanceResponse>> autoGenerateAttendances(@Valid @RequestBody AutoAttendanceRequest request) {
        List<AttendanceResponse> attendances = attendanceService.autoGenerateAttendances(request);
        return ApiResponse.success("Tạo chấm công tự động thành công. Tổng: " + attendances.size() + " ngày công", 
                attendances, HttpStatus.CREATED.value());
    }

    @GetMapping
    public ApiResponse<List<AttendanceResponse>> getAllAttendances() {
        List<AttendanceResponse> attendances = attendanceService.getAllAttendances();
        return ApiResponse.success("Lấy danh sách chấm công thành công", attendances, HttpStatus.OK.value());
    }

    @GetMapping("/filter")
    public ApiResponse<PageResponse<AttendanceResponse>> getAttendancesWithFilter(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<AttendanceResponse> attendances = attendanceService.getAttendancesWithFilter(
                keyword, month, year, page, pageSize);
        return ApiResponse.success("Lấy danh sách chấm công thành công", attendances, HttpStatus.OK.value());
    }

        @GetMapping("/employee/{employeeId}")
        public ApiResponse<PageResponse<AttendanceResponse>> getAttendancesByEmployee(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<AttendanceResponse> attendances = attendanceService.getAttendancesByEmployee(
            employeeId, month, year, page, pageSize);
        return ApiResponse.success("Lấy danh sách chấm công theo nhân viên thành công", attendances, HttpStatus.OK.value());
        }

    @GetMapping("/{id}")
    public ApiResponse<AttendanceResponse> getAttendanceById(@PathVariable Long id) {
        AttendanceResponse attendance = attendanceService.getAttendanceById(id);
        return ApiResponse.success("Lấy thông tin chấm công thành công", attendance, HttpStatus.OK.value());
    }

    @PutMapping("/{id}")
    public ApiResponse<AttendanceResponse> updateAttendance(
            @PathVariable Long id,
            @Valid @RequestBody AttendanceRequest request) {
        AttendanceResponse attendance = attendanceService.updateAttendance(id, request);
        return ApiResponse.success("Cập nhật chấm công thành công", attendance, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAttendance(@PathVariable Long id) {
        attendanceService.deleteAttendance(id);
        return ApiResponse.success("Xóa chấm công thành công", null, HttpStatus.OK.value());
    }

    @DeleteMapping("/by-date")
    public ApiResponse<Void> deleteByDateContractEmployee(@Valid @RequestBody com.company.company_clean_hub_be.dto.request.AttendanceDeleteRequest request) {
        attendanceService.softDeleteAttendance(request);
        return ApiResponse.success("Xóa chấm công theo ngày/hợp đồng/nhân viên thành công", null, HttpStatus.OK.value());
    }

    @PutMapping("/restore/{id}")
    public ApiResponse<Void> restoreAttendance(@PathVariable Long id) {
        attendanceService.restoreAttendance(id);
        return ApiResponse.success("Hoàn tác xóa chấm công thành công", null, HttpStatus.OK.value());
    }

    @PutMapping("/restore/by-date")
    public ApiResponse<Void> restoreByDateContractEmployee(@Valid @RequestBody com.company.company_clean_hub_be.dto.request.AttendanceRestoreRequest request) {
        attendanceService.restoreByDateContractEmployee(request);
        return ApiResponse.success("Hoàn tác xóa chấm công theo ngày/hợp đồng/nhân viên thành công", null, HttpStatus.OK.value());
    }

    @GetMapping("/total-days")
    public ApiResponse<TotalDaysResponse> getTotalDaysByEmployee(
            @RequestParam Long employeeId,
            @RequestParam Integer month,
            @RequestParam Integer year) {
        TotalDaysResponse response = attendanceService.getTotalDaysByEmployee(employeeId, month, year);
        return ApiResponse.success("Tính tổng công thành công", response, HttpStatus.OK.value());
    }

    @GetMapping("/contract/{contractId}")
    public ApiResponse<java.util.Map<Long, java.util.List<AttendanceResponse>>> getAttendancesByContract(
            @PathVariable Long contractId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year) {
        java.util.Map<Long, java.util.List<AttendanceResponse>> resp = attendanceService.getAttendancesByContractGroupedByEmployee(contractId, month, year);
        return ApiResponse.success("Lấy chấm công theo hợp đồng thành công", resp, HttpStatus.OK.value());
    }

    @GetMapping("/deleted")
    public ApiResponse<PageResponse<AttendanceResponse>> getDeletedAttendances(
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Long employeeId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageResponse<AttendanceResponse> resp = attendanceService.getDeletedAttendances(contractId, employeeId, month, year, page, pageSize);
        return ApiResponse.success("Lấy danh sách chấm công đã xóa thành công", resp, HttpStatus.OK.value());
    }
}
