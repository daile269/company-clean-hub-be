package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.AssignmentRequest;
import com.company.company_clean_hub_be.dto.request.TemporaryReassignmentRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.AssignmentResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.TemporaryAssignmentResponse;
import com.company.company_clean_hub_be.schedule.AssignmentScheduler;
import com.company.company_clean_hub_be.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/assignments")
public class AssignmentController {
    private final AssignmentService assignmentService;
    private final AssignmentScheduler assignmentScheduler;

    @GetMapping
    public ApiResponse<List<AssignmentResponse>> getAllAssignments() {
        List<AssignmentResponse> assignments = assignmentService.getAllAssignments();
        return ApiResponse.success("Lấy danh sách phân công thành công", assignments, HttpStatus.OK.value());
    }

    @GetMapping("/filter")
    public ApiResponse<PageResponse<AssignmentResponse>> getAssignmentsWithFilter(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<AssignmentResponse> assignments = assignmentService.getAssignmentsWithFilter(keyword, page, pageSize);
        return ApiResponse.success("Lấy danh sách phân công thành công", assignments, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    public ApiResponse<AssignmentResponse> getAssignmentById(@PathVariable Long id) {
        AssignmentResponse assignment = assignmentService.getAssignmentById(id);
        return ApiResponse.success("Lấy thông tin phân công thành công", assignment, HttpStatus.OK.value());
    }

    @PostMapping
    public ApiResponse<AssignmentResponse> createAssignment(@Valid @RequestBody AssignmentRequest request) {
        AssignmentResponse assignment = assignmentService.createAssignment(request);
        return ApiResponse.success("Tạo phân công thành công", assignment, HttpStatus.CREATED.value());
    }

    @PutMapping("/{id}")
    public ApiResponse<AssignmentResponse> updateAssignment(
            @PathVariable Long id,
            @Valid @RequestBody AssignmentRequest request) {
        AssignmentResponse assignment = assignmentService.updateAssignment(id, request);
        return ApiResponse.success("Cập nhật phân công thành công", assignment, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteAssignment(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ApiResponse.success("Xóa phân công thành công", null, HttpStatus.OK.value());
    }

    @PostMapping("/temporary-reassignment")
    public ApiResponse<TemporaryAssignmentResponse> temporaryReassignment(
            @Valid @RequestBody TemporaryReassignmentRequest request) {
        return ApiResponse.<TemporaryAssignmentResponse>builder()
                .data(assignmentService.temporaryReassignment(request))
                .success(true)
                .build();
    }

    @GetMapping("/customer/{customerId}")
    public ApiResponse<List<AssignmentResponse>> getEmployeesByCustomer(@PathVariable Long customerId) {
        List<AssignmentResponse> assignments = assignmentService.getEmployeesByCustomer(customerId);
        return ApiResponse.success(
                "Lấy danh sách nhân viên phụ trách khách hàng thành công", 
                assignments, 
                HttpStatus.OK.value()
        );
    }

    @GetMapping("/customer/{customerId}/all")
    public ApiResponse<List<AssignmentResponse>> getAllEmployeesByCustomer(@PathVariable Long customerId) {
        List<AssignmentResponse> assignments = assignmentService.getAllEmployeesByCustomer(customerId);
        return ApiResponse.success(
                "Lấy tất cả danh sách nhân viên phụ trách khách hàng thành công", 
                assignments, 
                HttpStatus.OK.value()
        );
    }

    @GetMapping("/employee/{employeeId}/customers")
    public ApiResponse<List<com.company.company_clean_hub_be.dto.response.CustomerResponse>> getCustomersByEmployee(
            @PathVariable Long employeeId) {
        List<com.company.company_clean_hub_be.dto.response.CustomerResponse> customers = assignmentService.getCustomersByEmployee(employeeId);
        return ApiResponse.success(
                "Lấy danh sách khách hàng nhân viên phụ trách thành công",
                customers,
                HttpStatus.OK.value()
        );
    }

    @GetMapping("/employee/{employeeId}")
    public ApiResponse<List<AssignmentResponse>> getAssignmentsByEmployee(@PathVariable Long employeeId) {
        List<AssignmentResponse> assignments = assignmentService.getAssignmentsByEmployee(employeeId);
        return ApiResponse.success(
                "Lấy danh sách phân công của nhân viên thành công",
                assignments,
                HttpStatus.OK.value()
        );
    }

    @GetMapping("/customer/{customerId}/not-assigned")
    public ApiResponse<PageResponse<com.company.company_clean_hub_be.dto.response.EmployeeResponse>> getEmployeesNotAssignedToCustomer(
            @PathVariable Long customerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<com.company.company_clean_hub_be.dto.response.EmployeeResponse> employees = 
                assignmentService.getEmployeesNotAssignedToCustomer(customerId, page, pageSize);
        return ApiResponse.success(
                "Lấy danh sách nhân viên chưa phân công thành công", 
                employees, 
                HttpStatus.OK.value()
        );
    }

    @PostMapping("/update-expired-temporary")
    public ApiResponse<String> testUpdateExpiredTemporaryAssignments() {
        assignmentScheduler.executeUpdateExpiredTemporaryAssignments();
        return ApiResponse.success(
                "Đã chạy job cập nhật phân công tạm thời, kiểm tra console log để xem kết quả", 
                "OK", 
                HttpStatus.OK.value()
        );
    }
}
