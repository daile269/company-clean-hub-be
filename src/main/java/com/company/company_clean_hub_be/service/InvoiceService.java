package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.InvoiceCreationRequest;
import com.company.company_clean_hub_be.dto.request.InvoiceUpdateRequest;
import com.company.company_clean_hub_be.dto.response.BulkInvoiceResponse;
import com.company.company_clean_hub_be.dto.response.InvoiceResponse;
import com.company.company_clean_hub_be.entity.InvoiceStatus;

import java.io.ByteArrayOutputStream;
import java.util.List;

public interface InvoiceService {
    InvoiceResponse createInvoice(InvoiceCreationRequest request);
    BulkInvoiceResponse createInvoicesForCustomer(InvoiceCreationRequest request);
    InvoiceResponse getInvoice(Long id);
    List<InvoiceResponse> getInvoicesByContract(Long contractId);
    List<InvoiceResponse> getInvoicesByCustomer(Long customerId);
    List<InvoiceResponse> getInvoicesByStatus(InvoiceStatus status);
    List<InvoiceResponse> getInvoicesByMonthAndYear(Integer month, Integer year);
    List<InvoiceResponse> getFullInvoicesByMonthAndYear(Integer month, Integer year);
    ByteArrayOutputStream exportInvoicesToExcel(Integer month, Integer year);
    com.company.company_clean_hub_be.dto.response.PageResponse<InvoiceResponse> getInvoicesWithFilters(String customerCode, Integer month, Integer year, int page, int pageSize);
    InvoiceResponse updateInvoice(Long id, InvoiceUpdateRequest request);
    void deleteInvoice(Long id);
    ByteArrayOutputStream exportInvoiceToExcel(Long invoiceId);
}
