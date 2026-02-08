package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.AssignmentRequest;
import com.company.company_clean_hub_be.dto.request.TemporaryReassignmentRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.AssignmentHistoryResponse;
import com.company.company_clean_hub_be.dto.response.AssignmentResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.RollbackResponse;
import com.company.company_clean_hub_be.dto.response.TemporaryAssignmentResponse;
import com.company.company_clean_hub_be.schedule.AssignmentScheduler;
import com.company.company_clean_hub_be.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/assignments")
public class AssignmentController {
    private final AssignmentService assignmentService;
    private final AssignmentScheduler assignmentScheduler;

    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public ApiResponse<List<AssignmentResponse>> getAllAssignments() {
        List<AssignmentResponse> assignments = assignmentService.getAllAssignments();
        return ApiResponse.success("Lấy danh sách phân công thành công", assignments, HttpStatus.OK.value());
    }

    @GetMapping("/filter")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public ApiResponse<PageResponse<AssignmentResponse>> getAssignmentsWithFilter(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<AssignmentResponse> assignments = assignmentService.getAssignmentsWithFilter(keyword, page,
                pageSize);
        return ApiResponse.success("Lấy danh sách phân công thành công", assignments, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public ApiResponse<AssignmentResponse> getAssignmentById(@PathVariable Long id) {
        AssignmentResponse assignment = assignmentService.getAssignmentById(id);
        System.out.println("id ass:" + id);
        return ApiResponse.success("Lấy thông tin phân công thành công", assignment, HttpStatus.OK.value());
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_CREATE')")
    public ApiResponse<AssignmentResponse> createAssignment(@Valid @RequestBody AssignmentRequest request) {
        AssignmentResponse assignment = assignmentService.createAssignment(request);
        return ApiResponse.success("Tạo phân công thành công", assignment, HttpStatus.CREATED.value());
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_UPDATE')")
    public ApiResponse<AssignmentResponse> updateAssignment(
            @PathVariable Long id,
            @Valid @RequestBody AssignmentRequest request) {

        log.info("[ASSIGNMENT][API][UPDATE] Start PUT /assignments/{} payload={}", id, request);

        if (id == null) {
            log.error("[ASSIGNMENT][API][UPDATE] Path variable id is null");
        }
        AssignmentResponse assignment = assignmentService.updateAssignment(id, request);

        log.info("[ASSIGNMENT][API][UPDATE] Update assignment success, id={}", assignment.getId());
        log.debug("[ASSIGNMENT][API][UPDATE] Updated assignment response={}", assignment);

        return ApiResponse.success(
                "Cập nhật phân công thành công",
                assignment,
                HttpStatus.OK.value());
    }


    @PutMapping("/{id}/allowance")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyAuthority  ('ASSIGNMENT_UPDATE','PAYROLL_EDIT')")
    public ApiResponse<AssignmentResponse> updateAllowanceAssignment(
            @PathVariable Long id,
            @Valid @RequestBody com.company.company_clean_hub_be.dto.request.UpdateAllowanceRequest request) {

        log.info("[ASSIGNMENT][API][UPDATE] Start PUT /assignments/{}/allowance allowance={}", id,
                request.getAllowance());

        if (id == null) {
            log.error("[ASSIGNMENT][API][UPDATE] Path variable id is null");
        }
        AssignmentResponse assignment = assignmentService.updateAllowanceAssignment(id, request.getAllowance());

        log.info("[ASSIGNMENT][API][UPDATE] Update assignment success, id={}", assignment.getId());
        log.debug("[ASSIGNMENT][API][UPDATE] Updated assignment response={}", assignment);

        return ApiResponse.success(
                "Cập nhật phân công thành công",
                assignment,
                HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_DELETE')")
    public ApiResponse<Void> deleteAssignment(@PathVariable Long id) {
        assignmentService.deleteAssignment(id);
        return ApiResponse.success("Xóa phân công thành công", null, HttpStatus.OK.value());
    }

    @PostMapping("/temporary-reassignment")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_REASSIGN')")
    public ApiResponse<TemporaryAssignmentResponse> temporaryReassignment(
            @Valid @RequestBody TemporaryReassignmentRequest request) {
        return ApiResponse.<TemporaryAssignmentResponse>builder()
                .data(assignmentService.temporaryReassignment(request))
                .success(true)
                .build();
    }

    @GetMapping("/customer/{customerId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW') or @securityCheck.isEmployeeAssignedToCustomer(#customerId)")
    public ApiResponse<List<AssignmentResponse>> getEmployeesByCustomer(@PathVariable Long customerId) {
        List<AssignmentResponse> assignments = assignmentService.getEmployeesByCustomer(customerId);
        return ApiResponse.success(
                "Lấy danh sách nhân viên phụ trách khách hàng thành công",
                assignments,
                HttpStatus.OK.value());
    }

    @GetMapping("/customer ./{customerId}/all")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW') or @securityCheck.isEmployeeAssignedToCustomer(#customerId)")
    public ApiResponse<PageResponse<AssignmentResponse>> getAllEmployeesByCustomer(
            @PathVariable Long customerId,
            @RequestParam(required = false) com.company.company_clean_hub_be.entity.ContractType contractType,
            @RequestParam(required = false) com.company.company_clean_hub_be.entity.AssignmentStatus status,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<AssignmentResponse> assignments = assignmentService.getAllEmployeesByCustomerWithFilters(
                customerId, contractType, status, month, year, page, pageSize);
        return ApiResponse.success(
                "Lấy tất cả danh sách nhân viên phụ trách khách hàng thành công",
                assignments,
                HttpStatus.OK.value());
    }

    @GetMapping("/customer/{customerId}/by-contract")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public ApiResponse<PageResponse<com.company.company_clean_hub_be.dto.response.AssignmentsByContractResponse>> getAssignmentsByCustomerGroupedByContract(
            @PathVariable Long customerId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) com.company.company_clean_hub_be.entity.ContractType contractType,
            @RequestParam(required = false) com.company.company_clean_hub_be.entity.AssignmentStatus status,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<com.company.company_clean_hub_be.dto.response.AssignmentsByContractResponse> assignments = assignmentService
                .getAssignmentsByCustomerGroupedByContract(
                        customerId, contractId, contractType, status, month, year, page, pageSize);
        return ApiResponse.success(
                "Lấy danh sách phân công theo hợp đồng thành công",
                assignments,
                HttpStatus.OK.value());
    }

    @GetMapping("/employee/{employeeId}/customers")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW') or @securityCheck.isEmployeeSelf(#employeeId)")
    public ApiResponse<List<com.company.company_clean_hub_be.dto.response.CustomerResponse>> getCustomersByEmployee(
            @PathVariable Long employeeId) {
        List<com.company.company_clean_hub_be.dto.response.CustomerResponse> customers = assignmentService
                .getCustomersByEmployee(employeeId);
        return ApiResponse.success(
                "Lấy danh sách khách hàng nhân viên phụ trách thành công",
                customers,
                HttpStatus.OK.value());
    }

    @GetMapping("/employee/{employeeId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW') or @securityCheck.isEmployeeSelf(#employeeId)")
    public ApiResponse<PageResponse<AssignmentResponse>> getAssignmentsByEmployee(
            @PathVariable Long employeeId,
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        PageResponse<AssignmentResponse> assignments = assignmentService.getAssignmentsByEmployeeWithFilters(
                employeeId, customerId, month, year, page, pageSize);

        return ApiResponse.success(
                "Lấy danh sách phân công của nhân viên thành công",
                assignments,
                HttpStatus.OK.value());
    }

    @GetMapping("/assignments/{employeeId}/{month}/" +
            "{year}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public ApiResponse<List<AssignmentResponse>> getAssignmentsByEmployeeMonthYear(@PathVariable Long employeeId,
            @PathVariable Integer month, @PathVariable Integer year) {
        List<AssignmentResponse> assignments = assignmentService.getAssignmentsByEmployeeMonthYear(employeeId, month,
                year);
        return ApiResponse.success(
                "Lấy danh sách phân công của nhân viên thành công",
                assignments,
                HttpStatus.OK.value());
    }

    @GetMapping("/customer/{customerId}/not-assigned")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public ApiResponse<PageResponse<com.company.company_clean_hub_be.dto.response.EmployeeResponse>> getEmployeesNotAssignedToCustomer(
            @PathVariable Long customerId,
            @RequestParam(required = false) com.company.company_clean_hub_be.entity.EmploymentType employmentType,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<com.company.company_clean_hub_be.dto.response.EmployeeResponse> employees = assignmentService
                .getEmployeesNotAssignedToCustomer(customerId, employmentType, month, year, page, pageSize);
        return ApiResponse.success(
                "Lấy danh sách nhân viên chưa phân công thành công",
                employees,
                HttpStatus.OK.value());
    }

    @GetMapping("/contract/{contractId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public ApiResponse<PageResponse<AssignmentResponse>> getAssignmentsByContract(
            @PathVariable Long contractId,
            @RequestParam(required = false) com.company.company_clean_hub_be.entity.AssignmentStatus status,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        PageResponse<AssignmentResponse> assignments = assignmentService.getAssignmentsByContract(contractId, status,
                month, year, page, pageSize);
        return ApiResponse.success("Lấy danh sách nhân viên phụ trách hợp đồng thành công", assignments,
                HttpStatus.OK.value());
    }

    @GetMapping("/{assignmentId}/attendances")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ATTENDANCE_VIEW')")
    public ApiResponse<PageResponse<com.company.company_clean_hub_be.dto.response.AttendanceResponse>> getAttendancesByAssignment(
            @PathVariable Long assignmentId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        PageResponse<com.company.company_clean_hub_be.dto.response.AttendanceResponse> attendances = assignmentService
                .getAttendancesByAssignment(assignmentId, month, year, page, pageSize);

        return ApiResponse.success("Lấy danh sách chấm công theo phân công thành công", attendances,
                HttpStatus.OK.value());
    }

    @PostMapping("/update-expired-temporary")
    public ApiResponse<String> testUpdateExpiredTemporaryAssignments() {
        assignmentScheduler.executeUpdateExpiredTemporaryAssignments();
        return ApiResponse.success(
                "Đã chạy job cập nhật phân công tạm thời, kiểm tra console log để xem kết quả",
                "OK",
                HttpStatus.OK.value());
    }

    @PostMapping("/update-expired-fixed")
    public ApiResponse<String> testUpdateExpiredFixedAssignments() {
        assignmentScheduler.executeUpdateExpiredFixedAssignments();
        return ApiResponse.success(
                "Đã chạy job cập nhật phân công cố định, kiểm tra console log để xem kết quả",
                "OK",
                HttpStatus.OK.value());
    }

    @PostMapping("/generate-monthly-attendances")
    public ApiResponse<String> testGenerateMonthlyAttendances() {
        assignmentScheduler.executeGenerateMonthlyAttendances();
        return ApiResponse.success(
                "Đã chạy job sinh chấm công tháng mới, kiểm tra console log để xem kết quả",
                "OK",
                HttpStatus.OK.value());
    }

    // ==================== LỊCH SỬ ĐIỀU ĐỘNG ====================

    @GetMapping("/history/employee/{employeeId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public ApiResponse<List<AssignmentHistoryResponse>> getReassignmentHistory(@PathVariable Long employeeId) {
        List<AssignmentHistoryResponse> history = assignmentService.getReassignmentHistory(employeeId);
        return ApiResponse.success(
                "Lấy lịch sử điều động thành công",
                history,
                HttpStatus.OK.value());
    }

    @GetMapping("/history/contract/{contractId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public ApiResponse<List<AssignmentHistoryResponse>> getReassignmentHistoryByContract(
            @PathVariable Long contractId) {
        List<AssignmentHistoryResponse> history = assignmentService.getReassignmentHistoryByContract(contractId);
        return ApiResponse.success(
                "Lấy lịch sử điều động theo hợp đồng thành công",
                history,
                HttpStatus.OK.value());
    }

    @GetMapping("/history/customer/{customerId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public ApiResponse<PageResponse<com.company.company_clean_hub_be.dto.response.ReassignmentHistoryByContractResponse>> getReassignmentHistoryByCustomerId(
            @PathVariable Long customerId,
            @RequestParam(required = false) Long contractId,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<com.company.company_clean_hub_be.dto.response.ReassignmentHistoryByContractResponse> history = assignmentService
                .getReassignmentHistoryByCustomerId(customerId, contractId, month, year, page, pageSize);
        return ApiResponse.success(
                "Lấy lịch sử điều động theo khách hàng thành công",
                history,
                HttpStatus.OK.value());
    }

    @GetMapping("/history/{historyId}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_VIEW')")
    public ApiResponse<AssignmentHistoryResponse> getHistoryDetail(@PathVariable Long historyId) {
        AssignmentHistoryResponse history = assignmentService.getHistoryDetail(historyId);
        return ApiResponse.success(
                "Lấy chi tiết lịch sử điều động thành công",
                history,
                HttpStatus.OK.value());
    }

    @PostMapping("/history/{historyId}/rollback")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_REASSIGN')")
    public ApiResponse<RollbackResponse> rollbackReassignment(@PathVariable Long historyId) {
        RollbackResponse response = assignmentService.rollbackReassignment(historyId);
        return ApiResponse.success(
                "Rollback điều động thành công",
                response,
                HttpStatus.OK.value());
    }

    @PutMapping("/{id}/terminate")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_UPDATE')")
    public ApiResponse<AssignmentResponse> terminateAssignment(
            @PathVariable Long id,
            @Valid @RequestBody com.company.company_clean_hub_be.dto.request.TerminateAssignmentRequest request) {
        log.info("[ASSIGNMENT][API][TERMINATE] Terminate assignment id={}, endDate={}", id, request.getEndDate());
        AssignmentResponse response = assignmentService.terminateAssignment(id, request);
        return ApiResponse.success(
                "Kết thúc assignment thành công",
                response,
                HttpStatus.OK.value());
    }

    @PostMapping("/{id}/terminate/rollback")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('ASSIGNMENT_UPDATE')")
    public ApiResponse<com.company.company_clean_hub_be.dto.response.RollbackTerminationResponse> rollbackTermination(
            @PathVariable Long id) {
        log.info("[ASSIGNMENT][API][ROLLBACK_TERMINATION] Rollback termination for assignment id={}", id);
        com.company.company_clean_hub_be.dto.response.RollbackTerminationResponse response = 
                assignmentService.rollbackTermination(id);
        return ApiResponse.success(
                "Hoàn tác kết thúc assignment thành công",
                response,
                HttpStatus.OK.value());
    }
}
