package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.InvoiceCreationRequest;
import com.company.company_clean_hub_be.dto.request.InvoiceUpdateRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.BulkInvoiceResponse;
import com.company.company_clean_hub_be.dto.response.InvoiceResponse;
import com.company.company_clean_hub_be.entity.InvoiceStatus;
import com.company.company_clean_hub_be.service.InvoiceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InvoiceController {

    InvoiceService invoiceService;

    @PostMapping
    public ApiResponse<InvoiceResponse> createInvoice(@RequestBody InvoiceCreationRequest request) {
        InvoiceResponse response = invoiceService.createInvoice(request);
        return ApiResponse.success(
                "Tạo hóa đơn thành công",
                response,
                HttpStatus.CREATED.value()
        );
    }

    @PostMapping("/customer/bulk")
    public ApiResponse<BulkInvoiceResponse> createInvoicesForCustomer(@RequestBody InvoiceCreationRequest request) {
        BulkInvoiceResponse response = invoiceService.createInvoicesForCustomer(request);
        return ApiResponse.success(
                "Tạo hóa đơn hàng loạt thành công",
                response,
                HttpStatus.CREATED.value()
        );
    }

    @GetMapping("/{id}")
    public ApiResponse<InvoiceResponse> getInvoice(@PathVariable Long id) {
        InvoiceResponse response = invoiceService.getInvoice(id);
        return ApiResponse.success(
                "Lấy thông tin hóa đơn thành công",
                response,
                HttpStatus.OK.value()
        );
    }

    @GetMapping("/contract/{contractId}")
    public ApiResponse<List<InvoiceResponse>> getInvoicesByContract(@PathVariable Long contractId) {
        List<InvoiceResponse> responses = invoiceService.getInvoicesByContract(contractId);
        return ApiResponse.success(
                "Lấy danh sách hóa đơn theo hợp đồng thành công",
                responses,
                HttpStatus.OK.value()
        );
    }

    @GetMapping("/customer/{customerId}")
    public ApiResponse<List<InvoiceResponse>> getInvoicesByCustomer(@PathVariable Long customerId) {
        List<InvoiceResponse> responses = invoiceService.getInvoicesByCustomer(customerId);
        return ApiResponse.success(
                "Lấy danh sách hóa đơn theo khách hàng thành công",
                responses,
                HttpStatus.OK.value()
        );
    }

    @GetMapping("/status/{status}")
    public ApiResponse<List<InvoiceResponse>> getInvoicesByStatus(@PathVariable InvoiceStatus status) {
        List<InvoiceResponse> responses = invoiceService.getInvoicesByStatus(status);
        return ApiResponse.success(
                "Lấy danh sách hóa đơn theo trạng thái thành công",
                responses,
                HttpStatus.OK.value()
        );
    }

    @GetMapping("/month/{month}/year/{year}")
    public ApiResponse<List<InvoiceResponse>> getInvoicesByMonthAndYear(
            @PathVariable Integer month,
            @PathVariable Integer year) {
        List<InvoiceResponse> responses = invoiceService.getInvoicesByMonthAndYear(month, year);
        return ApiResponse.success(
                "Lấy danh sách hóa đơn theo tháng/năm thành công",
                responses,
                HttpStatus.OK.value()
        );
    }

    @PutMapping("/{id}")
    public ApiResponse<InvoiceResponse> updateInvoice(
            @PathVariable Long id,
            @RequestBody InvoiceUpdateRequest request) {
        InvoiceResponse response = invoiceService.updateInvoice(id, request);
        return ApiResponse.success(
                "Cập nhật hóa đơn thành công",
                response,
                HttpStatus.OK.value()
        );
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteInvoice(@PathVariable Long id) {
        invoiceService.deleteInvoice(id);
        return ApiResponse.success(
                "Xóa hóa đơn thành công",
                null,
                HttpStatus.OK.value()
        );
    }
}