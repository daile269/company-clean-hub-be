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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class InvoiceController {

        InvoiceService invoiceService;

        @PostMapping
        @PreAuthorize("hasAuthority('INVOICE_CREATE')")
        public ApiResponse<InvoiceResponse> createInvoice(@RequestBody InvoiceCreationRequest request) {
                InvoiceResponse response = invoiceService.createInvoice(request);
                return ApiResponse.success(
                                "Tạo hóa đơn thành công",
                                response,
                                HttpStatus.CREATED.value());
        }

        @PostMapping("/customer/bulk")
        @PreAuthorize("hasAuthority('INVOICE_CREATE')")
        public ApiResponse<BulkInvoiceResponse> createInvoicesForCustomer(@RequestBody InvoiceCreationRequest request) {
                BulkInvoiceResponse response = invoiceService.createInvoicesForCustomer(request);
                return ApiResponse.success(
                                "Tạo hóa đơn hàng loạt thành công",
                                response,
                                HttpStatus.CREATED.value());
        }

        @GetMapping("/{id}")
        @PreAuthorize("hasAuthority('INVOICE_VIEW')")
        public ApiResponse<InvoiceResponse> getInvoice(@PathVariable Long id) {
                InvoiceResponse response = invoiceService.getInvoice(id);
                return ApiResponse.success(
                                "Lấy thông tin hóa đơn thành công",
                                response,
                                HttpStatus.OK.value());
        }

        @GetMapping("/contract/{contractId}")
        @PreAuthorize("hasAuthority('INVOICE_VIEW')")
        public ApiResponse<List<InvoiceResponse>> getInvoicesByContract(@PathVariable Long contractId) {
                List<InvoiceResponse> responses = invoiceService.getInvoicesByContract(contractId);
                return ApiResponse.success(
                                "Lấy danh sách hóa đơn theo hợp đồng thành công",
                                responses,
                                HttpStatus.OK.value());
        }

        @GetMapping("/customer/{customerId}")
        @PreAuthorize("hasAuthority('INVOICE_VIEW')")
        public ApiResponse<List<InvoiceResponse>> getInvoicesByCustomer(@PathVariable Long customerId) {
                List<InvoiceResponse> responses = invoiceService.getInvoicesByCustomer(customerId);
                return ApiResponse.success(
                                "Lấy danh sách hóa đơn theo khách hàng thành công",
                                responses,
                                HttpStatus.OK.value());
        }

        @GetMapping("/status/{status}")
        @PreAuthorize("hasAuthority('INVOICE_VIEW')")
        public ApiResponse<List<InvoiceResponse>> getInvoicesByStatus(@PathVariable InvoiceStatus status) {
                List<InvoiceResponse> responses = invoiceService.getInvoicesByStatus(status);
                return ApiResponse.success(
                                "Lấy danh sách hóa đơn theo trạng thái thành công",
                                responses,
                                HttpStatus.OK.value());
        }

        @GetMapping("/month/{month}/year/{year}")
        @PreAuthorize("hasAuthority('INVOICE_VIEW')")
        public ApiResponse<List<InvoiceResponse>> getInvoicesByMonthAndYear(
                        @PathVariable Integer month,
                        @PathVariable Integer year) {
                List<InvoiceResponse> responses = invoiceService.getInvoicesByMonthAndYear(month, year);
                return ApiResponse.success(
                                "Lấy danh sách hóa đơn theo tháng/năm thành công",
                                responses,
                                HttpStatus.OK.value());
        }

        @GetMapping("/month/{month}/year/{year}/full")
        @PreAuthorize("hasAuthority('INVOICE_VIEW')")
        public ApiResponse<List<InvoiceResponse>> getFullInvoicesByMonthAndYear(
                        @PathVariable Integer month,
                        @PathVariable Integer year) {
                List<InvoiceResponse> responses = invoiceService.getFullInvoicesByMonthAndYear(month, year);
                return ApiResponse.success(
                                "Lấy đầy đủ hóa đơn (có invoice lines) theo tháng/năm thành công",
                                responses,
                                HttpStatus.OK.value());
        }

        @GetMapping("/filter")
        @PreAuthorize("hasAuthority('INVOICE_VIEW')")
        public ApiResponse<com.company.company_clean_hub_be.dto.response.PageResponse<InvoiceResponse>> getInvoicesWithFilters(
                        @RequestParam(required = false) String customerCode,
                        @RequestParam(required = false) Integer month,
                        @RequestParam(required = false) Integer year,
                        @RequestParam(defaultValue = "1") int page,
                        @RequestParam(defaultValue = "10") int pageSize) {
                com.company.company_clean_hub_be.dto.response.PageResponse<InvoiceResponse> resp = invoiceService
                                .getInvoicesWithFilters(customerCode, month, year, page, pageSize);
                return ApiResponse.success("Lấy danh sách hóa đơn có lọc thành công", resp, HttpStatus.OK.value());
        }

        @PutMapping("/{id}")
        @PreAuthorize("hasAuthority('INVOICE_EDIT')")
        public ApiResponse<InvoiceResponse> updateInvoice(
                        @PathVariable Long id,
                        @RequestBody InvoiceUpdateRequest request) {
                InvoiceResponse response = invoiceService.updateInvoice(id, request);
                return ApiResponse.success(
                                "Cập nhật hóa đơn thành công",
                                response,
                                HttpStatus.OK.value());
        }

        @DeleteMapping("/{id}")
        @PreAuthorize("hasAuthority('INVOICE_DELETE')")
        public ApiResponse<Void> deleteInvoice(@PathVariable Long id) {
                invoiceService.deleteInvoice(id);
                return ApiResponse.success(
                                "Xóa hóa đơn thành công",
                                null,
                                HttpStatus.OK.value());
        }

        @GetMapping("/{id}/export/excel")
        @PreAuthorize("hasAuthority('INVOICE_EXPORT')")
        public ResponseEntity<byte[]> exportInvoiceToExcel(@PathVariable Long id) {
                ByteArrayOutputStream outputStream = invoiceService.exportInvoiceToExcel(id);

                InvoiceResponse invoiceResponse = invoiceService.getInvoice(id);
                String filename = URLEncoder.encode(
                                "Hóa đơn " + invoiceResponse.getCustomerName()
                                                + "_HĐ_"
                                                + invoiceResponse.getContractId()
                                                + "_"
                                                + invoiceResponse.getInvoiceMonth()
                                                + "-"
                                                + invoiceResponse.getInvoiceYear()
                                                + ".xlsx",
                                StandardCharsets.UTF_8).replaceAll("\\+", "%20");
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
                headers.setContentDispositionFormData("attachment", filename);
                headers.setContentLength(outputStream.size());

                return new ResponseEntity<>(outputStream.toByteArray(), headers, HttpStatus.OK);
        }

        @GetMapping("/month/{month}/year/{year}/export/excel")
        @PreAuthorize("hasAuthority('INVOICE_EXPORT')")
        public ResponseEntity<byte[]> exportInvoicesToExcel(@PathVariable Integer month, @PathVariable Integer year) {
                ByteArrayOutputStream outputStream = invoiceService.exportInvoicesToExcel(month, year);

                String rawFilename = "Invoices_" + month + "-" + year + ".zip";
                String encodedFilename = URLEncoder.encode(rawFilename, StandardCharsets.UTF_8).replaceAll("\\+", "%20");

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(org.springframework.http.MediaType.parseMediaType("application/zip"));
                headers.add(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + rawFilename + "\"; filename*=UTF-8''" + encodedFilename);
                headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
                headers.setContentLength(outputStream.size());

                return new ResponseEntity<>(outputStream.toByteArray(), headers, HttpStatus.OK);
        }
}