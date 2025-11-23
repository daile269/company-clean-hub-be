package com.company.company_clean_hub_be.controller;

import com.company.company_clean_hub_be.dto.request.ContractRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.ContractResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.service.ContractService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping(value = "/api/contracts")
public class ContractController {
    private final ContractService contractService;

    @GetMapping
    public ApiResponse<List<ContractResponse>> getAllContracts() {
        List<ContractResponse> contracts = contractService.getAllContracts();
        return ApiResponse.success("Lấy danh sách hợp đồng thành công", contracts, HttpStatus.OK.value());
    }

    @GetMapping("/filter")
    public ApiResponse<PageResponse<ContractResponse>> getContractsWithFilter(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        PageResponse<ContractResponse> contracts = contractService.getContractsWithFilter(keyword, page, pageSize);
        return ApiResponse.success("Lấy danh sách hợp đồng thành công", contracts, HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    public ApiResponse<ContractResponse> getContractById(@PathVariable Long id) {
        ContractResponse contract = contractService.getContractById(id);
        return ApiResponse.success("Lấy thông tin hợp đồng thành công", contract, HttpStatus.OK.value());
    }

    @PostMapping
    public ApiResponse<ContractResponse> createContract(@Valid @RequestBody ContractRequest request) {
        ContractResponse contract = contractService.createContract(request);
        return ApiResponse.success("Tạo hợp đồng thành công", contract, HttpStatus.CREATED.value());
    }

    @PutMapping("/{id}")
    public ApiResponse<ContractResponse> updateContract(
            @PathVariable Long id,
            @Valid @RequestBody ContractRequest request) {
        ContractResponse contract = contractService.updateContract(id, request);
        return ApiResponse.success("Cập nhật hợp đồng thành công", contract, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteContract(@PathVariable Long id) {
        contractService.deleteContract(id);
        return ApiResponse.success("Xóa hợp đồng thành công", null, HttpStatus.OK.value());
    }
}
