package com.company.company_clean_hub_be.service;

import java.math.BigDecimal;
import java.util.List;

import com.company.company_clean_hub_be.dto.request.PayrollRequest;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.PayRollAssignmentExportExcel;
import com.company.company_clean_hub_be.dto.response.PayrollAssignmentResponse;
import com.company.company_clean_hub_be.dto.response.PayrollResponse;
import com.company.company_clean_hub_be.dto.response.PaymentHistoryResponse;

import jakarta.validation.Valid;

public interface PayrollService {
    // List<PayRollExportExcel> getAllPayRoll(Integer month, Integer year);

    List<PayRollAssignmentExportExcel> getAllPayRollByAssignment(Integer month, Integer year);

    // PayrollResponse calculatePayroll(PayrollRequest request);
    PayrollResponse getPayrollById(Long id);

    List<PayrollResponse> getAllPayrolls();

    PageResponse<PayrollResponse> getPayrollsWithFilter(String keyword, Integer month, Integer year, Boolean isPaid,
            int page, int pageSize);

    PayrollResponse updatePaymentStatus(Long id, BigDecimal paidAmount);

    void deletePayroll(Long id);

    // Updated to return List for bulk calculation
    List<PayrollAssignmentResponse> calculatePayroll(@Valid PayrollRequest request);

    // New method for filtered assignments with pagination
    PageResponse<PayrollAssignmentResponse> getPayrollAssignmentsWithFilter(
            String keyword, Integer month, Integer year, int page, int pageSize);

    PayrollResponse updatePayroll(Long id, com.company.company_clean_hub_be.dto.request.PayrollUpdateRequest request);

    // Payment history methods
    List<PaymentHistoryResponse> getPaymentHistory(Long payrollId);
}
