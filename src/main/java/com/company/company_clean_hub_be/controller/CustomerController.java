package com.company.company_clean_hub_be.controller;

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

import com.company.company_clean_hub_be.dto.request.CustomerRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.CustomerContractGroupDto;
import com.company.company_clean_hub_be.dto.response.CustomerResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.service.CustomerService;
import com.company.company_clean_hub_be.service.ExcelExportService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/customers")
@Slf4j
public class CustomerController {
    private final CustomerService customerService;
    private final ExcelExportService excelExportService;

    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ApiResponse<List<CustomerResponse>> getAllCustomers() {
        List<CustomerResponse> customers = customerService.getAllCustomers();
        return ApiResponse.success("Lấy danh sách khách hàng thành công", customers, HttpStatus.OK.value());
    }

    @GetMapping("/filter")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ApiResponse<PageResponse<CustomerResponse>> getCustomersWithFilter(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<CustomerResponse> customers = customerService.getCustomersWithFilter(keyword, page, pageSize);
        return ApiResponse.success("Lấy danh sách khách hàng thành công", customers, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('CUSTOMER_VIEW')")
    public ApiResponse<CustomerResponse> getCustomerById(@PathVariable Long id) {
        CustomerResponse customer = customerService.getCustomerById(id);
        return ApiResponse.success("Lấy thông tin khách hàng thành công", customer, HttpStatus.OK.value());
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('CUSTOMER_CREATE')")
    public ApiResponse<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest request) {
        CustomerResponse customer = customerService.createCustomer(request);
        return ApiResponse.success("Tạo khách hàng thành công", customer, HttpStatus.CREATED.value());
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('CUSTOMER_EDIT')")
    public ApiResponse<CustomerResponse> updateCustomer(
            @PathVariable Long id,
            @Valid @RequestBody CustomerRequest request) {
        CustomerResponse customer = customerService.updateCustomer(id, request);
        return ApiResponse.success("Cập nhật khách hàng thành công", customer, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('CUSTOMER_DELETE')")
    public ApiResponse<Void> deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ApiResponse.success("Xóa khách hàng thành công", null, HttpStatus.OK.value());
    }

    @GetMapping("/export/excel")
    public ResponseEntity<ByteArrayResource> exportCustomersWithContracts() {
        log.info("Export customers with contracts requested");
        try {
            List<CustomerContractGroupDto> customerGroups = customerService.getCustomersWithContractsForExport();
            ByteArrayResource resource = excelExportService.exportCustomersWithContractsToExcel(customerGroups);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=Danh_sach_khach_hang.xlsx")
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            log.error("Error exporting customers", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
