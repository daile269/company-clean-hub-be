package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.PayrollRequest;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.PayrollResponse;

import java.util.List;

public interface PayrollService {
//    PayrollResponse calculatePayroll(PayrollRequest request);
    PayrollResponse getPayrollById(Long id);
    List<PayrollResponse> getAllPayrolls();
    PageResponse<PayrollResponse> getPayrollsWithFilter(String keyword, Integer month, Integer year, Boolean isPaid, int page, int pageSize);
    PayrollResponse updatePaymentStatus(Long id, Boolean isPaid);
    void deletePayroll(Long id);
}
