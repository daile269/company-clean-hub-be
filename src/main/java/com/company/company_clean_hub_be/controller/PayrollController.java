package com.company.company_clean_hub_be.controller;

import java.util.List;

import com.company.company_clean_hub_be.dto.response.PayRollAssignmentExportExcel;
import com.company.company_clean_hub_be.service.ExcelExportService;
import com.company.company_clean_hub_be.service.impl.ExcelExportServiceImpl;
import com.company.company_clean_hub_be.service.impl.PayrollServiceImpl;
import lombok.extern.slf4j.Slf4j;
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

import com.company.company_clean_hub_be.dto.request.PayrollRequest;
import com.company.company_clean_hub_be.dto.request.PayrollUpdateRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.PayrollResponse;
import com.company.company_clean_hub_be.service.PayrollService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/payrolls")
@Slf4j
public class PayrollController {
    private final PayrollService payrollService;
    private final ExcelExportService excelExportService;
    @PostMapping("/calculate")
    public ApiResponse<PayrollResponse> calculatePayroll(@Valid @RequestBody PayrollRequest request) {
        PayrollResponse payroll = payrollService.calculatePayroll(request);
        if (payroll == null){
            return ApiResponse.success("Nhân viên không có công làm trong thời gian này ", null, HttpStatus.CREATED.value());
        }
        return ApiResponse.success("Tính lương thành công", payroll, HttpStatus.CREATED.value());
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<PayrollResponse> payrolls = payrollService.getPayrollsWithFilter(
                keyword, month, year, isPaid, page, pageSize);
        return ApiResponse.success("Lấy danh sách bảng lương thành công", payrolls, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    public ApiResponse<PayrollResponse> getPayrollById(@PathVariable Long id) {
        PayrollResponse payroll = payrollService.getPayrollById(id);
        return ApiResponse.success("Lấy thông tin bảng lương thành công", payroll, HttpStatus.OK.value());
    }

    @PutMapping("/{id}/payment-status")
    public ApiResponse<PayrollResponse> updatePaymentStatus(
            @PathVariable Long id,
            @RequestParam Boolean isPaid) {
        PayrollResponse payroll = payrollService.updatePaymentStatus(id, isPaid);
        return ApiResponse.success("Cập nhật trạng thái thanh toán thành công", payroll, HttpStatus.OK.value());
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
    @GetMapping("export/excel/{month}/{year}")
    public ResponseEntity<ByteArrayResource> exportPayroll(
            @PathVariable Integer month,
            @PathVariable Integer year) {

        log.debug("🔵 [EXPORT PAYROLL] Request nhận được: month={}, year={}", month, year);
        List<PayRollAssignmentExportExcel> assignmentData = payrollService.getAllPayRollByAssignment(month, year);
        log.debug("🟢 [EXPORT PAYROLL] Số lượng dòng payroll lấy được: {}",
                assignmentData != null ? assignmentData.size() : 0);

        // Use new export method
        ExcelExportServiceImpl excelExportServiceImpl = (ExcelExportServiceImpl) excelExportService;
        ByteArrayResource excelFile = excelExportServiceImpl.exportPayrollAssignmentsToExcel(assignmentData, month, year);

        if (excelFile == null) {
            log.warn("⚠️ [EXPORT PAYROLL] excelFile = null → Không tạo được file Excel!");
        } else {
            log.debug("🟩 [EXPORT PAYROLL] File Excel đã tạo. Kích thước: {} bytes",
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



}
