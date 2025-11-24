package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.ServiceRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.ServiceResponse;
import com.company.company_clean_hub_be.service.ServiceEntityService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/services")
public class ServiceController {
    private final ServiceEntityService serviceEntityService;

    @GetMapping
    public ApiResponse<List<ServiceResponse>> getAllServices() {
        List<ServiceResponse> services = serviceEntityService.getAllServices();
        return ApiResponse.success("Lấy danh sách dịch vụ thành công", services, HttpStatus.OK.value());
    }

    @GetMapping("/filter")
    public ApiResponse<PageResponse<ServiceResponse>> getServicesWithFilter(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<ServiceResponse> services = serviceEntityService.getServicesWithFilter(keyword, page, pageSize);
        return ApiResponse.success("Lấy danh sách dịch vụ thành công", services, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    public ApiResponse<ServiceResponse> getServiceById(@PathVariable Long id) {
        ServiceResponse service = serviceEntityService.getServiceById(id);
        return ApiResponse.success("Lấy thông tin dịch vụ thành công", service, HttpStatus.OK.value());
    }

    @PostMapping
    public ApiResponse<ServiceResponse> createService(@Valid @RequestBody ServiceRequest request) {
        ServiceResponse service = serviceEntityService.createService(request);
        return ApiResponse.success("Tạo dịch vụ thành công", service, HttpStatus.CREATED.value());
    }

    @PutMapping("/{id}")
    public ApiResponse<ServiceResponse> updateService(
            @PathVariable Long id,
            @Valid @RequestBody ServiceRequest request) {
        ServiceResponse service = serviceEntityService.updateService(id, request);
        return ApiResponse.success("Cập nhật dịch vụ thành công", service, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteService(@PathVariable Long id) {
        serviceEntityService.deleteService(id);
        return ApiResponse.success("Xóa dịch vụ thành công", null, HttpStatus.OK.value());
    }
}
