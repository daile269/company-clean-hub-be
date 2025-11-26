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

    @GetMapping("/total-days")
    public ApiResponse<TotalDaysResponse> getTotalDaysByEmployee(
            @RequestParam Long employeeId,
            @RequestParam Integer month,
            @RequestParam Integer year) {
        TotalDaysResponse response = attendanceService.getTotalDaysByEmployee(employeeId, month, year);
        return ApiResponse.success("Tính tổng công thành công", response, HttpStatus.OK.value());
    }
}
