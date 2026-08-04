package com.company.company_clean_hub_be.controller;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.company.company_clean_hub_be.dto.request.PaymentRequest;
import com.company.company_clean_hub_be.dto.request.PayrollRequest;
import com.company.company_clean_hub_be.dto.request.PayrollUpdateRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.PayRollAssignmentExportExcel;
import com.company.company_clean_hub_be.dto.response.PaymentHistoryResponse;
import com.company.company_clean_hub_be.dto.response.PayrollAssignmentResponse;
import com.company.company_clean_hub_be.dto.response.PayrollOverviewResponse;
import com.company.company_clean_hub_be.dto.response.PayrollResponse;
import com.company.company_clean_hub_be.service.ExcelExportService;
import com.company.company_clean_hub_be.service.PayrollService;
import com.company.company_clean_hub_be.service.impl.ExcelExportServiceImpl;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/payrolls")
@Slf4j
public class PayrollController {
    private final PayrollService payrollService;
    private final ExcelExportService excelExportService;

    @PostMapping("/calculate")
    public ApiResponse<List<PayrollAssignmentResponse>> calculatePayroll(@Valid @RequestBody PayrollRequest request) {
        List<PayrollAssignmentResponse> payrolls = payrollService.calculatePayroll(request);
        return ApiResponse.success("Tính lương thành công", payrolls, HttpStatus.CREATED.value());
    }

    @GetMapping
    public ApiResponse<List<PayrollResponse>> getAllPayrolls() {
        List<PayrollResponse> payrolls = payrollService.getAllPayrolls();
        return ApiResponse.success("Lấy danh sách bảng lương thành công", payrolls, HttpStatus.OK.value());
    }

    @GetMapping("/filter")
    public ApiResponse<PageResponse<PayrollResponse>> getPayrollsWithFilter(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Boolean isPaid,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<PayrollResponse> payrolls = payrollService.getPayrollsWithFilter(
                keyword, month, year, isPaid, sortBy, sortDirection, page, pageSize);
        return ApiResponse.success("Lấy danh sách bảng lương thành công", payrolls, HttpStatus.OK.value());
    }

    @GetMapping("/overview")
    public ApiResponse<PayrollOverviewResponse> getPayrollOverview(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Boolean isPaid) {
        PayrollOverviewResponse overview = payrollService.getPayrollOverview(keyword, month, year, isPaid);
        return ApiResponse.success("Lấy tổng quan bảng lương thành công", overview, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    public ApiResponse<PayrollResponse> getPayrollById(@PathVariable Long id) {
        PayrollResponse payroll = payrollService.getPayrollById(id);
        return ApiResponse.success("Lấy thông tin bảng lương thành công", payroll, HttpStatus.OK.value());
    }

    @PutMapping("/{id}/payment-status")
    public ApiResponse<PayrollResponse> updatePaymentStatus(
            @PathVariable Long id,
            @RequestParam BigDecimal paidAmount) {
        PayrollResponse payroll = payrollService.updatePaymentStatus(id, paidAmount);
        return ApiResponse.success("Cập nhật thanh toán thành công", payroll, HttpStatus.OK.value());
    }

    @PostMapping("/{id}/payment")
    public ApiResponse<PayrollResponse> processPayment(
            @PathVariable Long id,
            @Valid @RequestBody PaymentRequest request) {
        PayrollResponse payroll = payrollService.processPayment(id, request);
        return ApiResponse.success("Thanh toán lương thành công", payroll, HttpStatus.OK.value());
    }

    @GetMapping("/{id}/payment-warning")
    public ApiResponse<String> getPaymentWarning(
            @PathVariable Long id,
            @RequestParam BigDecimal amount) {
        PayrollResponse payroll = payrollService.getPayrollById(id);
        BigDecimal remaining = payroll.getFinalSalary().subtract(payroll.getPaidAmount());
        if (amount.compareTo(remaining) < 0) {
            return ApiResponse.success(
                    String.format("Số tiền thanh toán (%,.0f ₫) nhỏ hơn số dư còn lại (%,.0f ₫). Bạn có chắc muốn tiếp tục?",
                            amount, remaining),
                    null, HttpStatus.OK.value());
        }
        return ApiResponse.success(null, null, HttpStatus.OK.value());
    }

    @PutMapping("/{id}/recalculate")
    public ApiResponse<PayrollResponse> updatePayroll(
            @PathVariable Long id,
            @RequestBody PayrollUpdateRequest request) {
        PayrollResponse payroll = payrollService.updatePayroll(id, request);
        return ApiResponse.success("Cập nhật và tính lại bảng lương thành công", payroll, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePayroll(@PathVariable Long id) {
        payrollService.deletePayroll(id);
        return ApiResponse.success("Xóa bảng lương thành công", null, HttpStatus.OK.value());
    }

    @GetMapping("/assignments/filter")
    public ApiResponse<PageResponse<PayrollAssignmentResponse>> getPayrollAssignmentsWithFilter(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer month,
            @RequestParam(required = false) Integer year,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<PayrollAssignmentResponse> result = payrollService.getPayrollAssignmentsWithFilter(keyword, month,
                year, page, pageSize);
        return ApiResponse.success("Lấy danh sách thành công", result, HttpStatus.OK.value());
    }

    @GetMapping("export/excel/{month}/{year}")
    public ResponseEntity<ByteArrayResource> exportPayroll(
            @PathVariable Integer month,
            @PathVariable Integer year) {

        log.info("🔵 [EXPORT PAYROLL] Request nhận được: month={}, year={}", month, year);
        List<PayRollAssignmentExportExcel> assignmentData = payrollService.getAllPayRollByAssignment(month, year);
        log.info("🟢 [EXPORT PAYROLL] Số lượng dòng payroll lấy được: {}",
                assignmentData != null ? assignmentData.size() : 0);
        ByteArrayResource excelFile = excelExportService.exportPayrollAssignmentsToExcel(assignmentData, month, year);

        if (excelFile == null) {
            log.warn("⚠️ [EXPORT PAYROLL] excelFile = null → Không tạo được file Excel!");
        } else {
            log.info("🟩 [EXPORT PAYROLL] File Excel đã tạo. Kích thước: {} bytes",
                    excelFile.contentLength());
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=payroll_" + month + "_" + year + ".xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(excelFile.contentLength())
                .body(excelFile);
    }

    @GetMapping("/{id}/payment-history")
    public ApiResponse<List<PaymentHistoryResponse>> getPaymentHistory(@PathVariable Long id) {
        List<PaymentHistoryResponse> history = payrollService.getPaymentHistory(id);
        return ApiResponse.success("Lấy lịch sử thanh toán thành công", history, HttpStatus.OK.value());
    }

    @GetMapping("/employee/{employeeId}/assignment-details")
    public ApiResponse<List<com.company.company_clean_hub_be.dto.response.AssignmentPayrollDetailResponse>> getAssignmentPayrollDetails(
            @PathVariable Long employeeId,
            @RequestParam Integer month,
            @RequestParam Integer year) {
        List<com.company.company_clean_hub_be.dto.response.AssignmentPayrollDetailResponse> details = payrollService
                .getAssignmentPayrollDetails(employeeId, month, year);
        return ApiResponse.success("Lấy chi tiết lương assignment thành công", details, HttpStatus.OK.value());
    }

    @GetMapping("/years")
    public ApiResponse<List<Integer>> getDistinctYears() {
        List<Integer> years = payrollService.getDistinctYears();
        return ApiResponse.success("Lấy danh sách năm thành công", years, HttpStatus.OK.value());
    }

}
