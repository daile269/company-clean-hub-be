package com.company.company_clean_hub_be.service;

import java.util.List;

import com.company.company_clean_hub_be.dto.request.PayrollRequest;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.PayRollAssignmentExportExcel;
import com.company.company_clean_hub_be.dto.response.PayRollExportExcel;
import com.company.company_clean_hub_be.dto.response.PayrollResponse;

import jakarta.validation.Valid;

public interface PayrollService {
//    List<PayRollExportExcel> getAllPayRoll(Integer month, Integer year);

    List<PayRollAssignmentExportExcel> getAllPayRollByAssignment(Integer month, Integer year);

    //    PayrollResponse calculatePayroll(PayrollRequest request);
    PayrollResponse getPayrollById(Long id);
    List<PayrollResponse> getAllPayrolls();
    PageResponse<PayrollResponse> getPayrollsWithFilter(String keyword, Integer month, Integer year, Boolean isPaid, int page, int pageSize);
    PayrollResponse updatePaymentStatus(Long id, Boolean isPaid);
    void deletePayroll(Long id);

    PayrollResponse calculatePayroll(@Valid PayrollRequest request);
    PayrollResponse updatePayroll(Long id, com.company.company_clean_hub_be.dto.request.PayrollUpdateRequest request);
}
