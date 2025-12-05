package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.InvoiceCreationRequest;
import com.company.company_clean_hub_be.dto.request.InvoiceUpdateRequest;
import com.company.company_clean_hub_be.dto.response.BulkInvoiceResponse;
import com.company.company_clean_hub_be.dto.response.InvoiceResponse;
import com.company.company_clean_hub_be.entity.InvoiceStatus;
import com.company.company_clean_hub_be.service.InvoiceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InvoiceController {

    InvoiceService invoiceService;

    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(@RequestBody InvoiceCreationRequest request) {
        return ResponseEntity.ok(invoiceService.createInvoice(request));
    }

    @PostMapping("/customer/bulk")
    public ResponseEntity<BulkInvoiceResponse> createInvoicesForCustomer(@RequestBody InvoiceCreationRequest request) {
        return ResponseEntity.ok(invoiceService.createInvoicesForCustomer(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getInvoice(id));
    }

    @GetMapping("/contract/{contractId}")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByContract(@PathVariable Long contractId) {
        return ResponseEntity.ok(invoiceService.getInvoicesByContract(contractId));
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(invoiceService.getInvoicesByCustomer(customerId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByStatus(@PathVariable InvoiceStatus status) {
        return ResponseEntity.ok(invoiceService.getInvoicesByStatus(status));
    }

    @GetMapping("/month/{month}/year/{year}")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByMonthAndYear(
            @PathVariable Integer month,
            @PathVariable Integer year) {
        return ResponseEntity.ok(invoiceService.getInvoicesByMonthAndYear(month, year));
    }

    @PutMapping("/{id}")
    public ResponseEntity<InvoiceResponse> updateInvoice(
            @PathVariable Long id,
            @RequestBody InvoiceUpdateRequest request) {
        return ResponseEntity.ok(invoiceService.updateInvoice(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteInvoice(@PathVariable Long id) {
        invoiceService.deleteInvoice(id);
        return ResponseEntity.noContent().build();
    }
}
