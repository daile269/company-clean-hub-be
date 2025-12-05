package com.company.company_clean_hub_be.controller;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.company.company_clean_hub_be.dto.request.ContractRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.ContractResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.entity.ContractDocument;
import com.company.company_clean_hub_be.service.ContractDocumentService;
import com.company.company_clean_hub_be.service.ContractService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping(value = "/api/contracts")
public class ContractController {
    private final ContractService contractService;
    private final ContractDocumentService contractDocumentService;

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

    @GetMapping("/customer/{customerId}")
    public ApiResponse<List<ContractResponse>> getContractsByCustomer(@PathVariable Long customerId) {
        List<ContractResponse> contracts = contractService.getContractsByCustomer(customerId);
        return ApiResponse.success("Lấy danh sách hợp đồng của khách hàng thành công", contracts, HttpStatus.OK.value());
    }

    @GetMapping("/assignment/{assignmentId}")
    public ApiResponse<ContractResponse> getContractByAssignmentId(@PathVariable Long assignmentId) {
        ContractResponse contract = contractService.getContractByAssignmentId(assignmentId);
        return ApiResponse.success("Lấy thông tin hợp đồng từ phân công thành công", contract, HttpStatus.OK.value());
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

    @PostMapping("/{contractId}/services/{serviceId}")
    public ApiResponse<ContractResponse> addServiceToContract(
            @PathVariable Long contractId,
            @PathVariable Long serviceId) {
        ContractResponse contract = contractService.addServiceToContract(contractId, serviceId);
        return ApiResponse.success("Thêm dịch vụ vào hợp đồng thành công", contract, HttpStatus.OK.value());
    }

    @DeleteMapping("/{contractId}/services/{serviceId}")
    public ApiResponse<ContractResponse> removeServiceFromContract(
            @PathVariable Long contractId,
            @PathVariable Long serviceId) {
        ContractResponse contract = contractService.removeServiceFromContract(contractId, serviceId);
        return ApiResponse.success("Xóa dịch vụ khỏi hợp đồng thành công", contract, HttpStatus.OK.value());
    }

    @PostMapping(value = "/{id}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<List<ContractDocument>> uploadContractDocuments(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile[] files) throws IOException {

        log.info("Start uploadContractDocuments: contractId={}, fileCount={}", id, files.length);
        
        List<ContractDocument> uploadedDocuments = contractDocumentService.uploadDocuments(id, files);
        
        return ApiResponse.success("Tải tài liệu hợp đồng thành công", uploadedDocuments, HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}/documents/{documentId}")
    public ApiResponse<Void> deleteContractDocument(
            @PathVariable Long id,
            @PathVariable Long documentId) throws IOException {

        log.info("Start deleteContractDocument: contractId={}, documentId={}", id, documentId);
        
        contractDocumentService.deleteDocument(id, documentId);
        
        return ApiResponse.success("Xóa tài liệu hợp đồng thành công", null, HttpStatus.OK.value());
    }

    @GetMapping("/{id}/documents")
    public ApiResponse<List<ContractDocument>> getContractDocuments(@PathVariable Long id) {
        log.info("Fetching contract documents: contractId={}", id);
        
        List<ContractDocument> documents = contractDocumentService.getContractDocuments(id);
        
        return ApiResponse.success("Lấy tài liệu hợp đồng thành công", documents, HttpStatus.OK.value());
    }
}
