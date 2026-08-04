package com.company.company_clean_hub_be.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.company.company_clean_hub_be.dto.request.SalaryNoteRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.SalaryNoteResponse;
import com.company.company_clean_hub_be.service.SalaryNoteService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

@RestController
@RequestMapping("/api/contracts/{contractId}/salary-notes")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SalaryNoteController {

    SalaryNoteService salaryNoteService;

    @GetMapping
    @PreAuthorize("hasAnyRole('QLT1', 'QLT2', 'QLV')")
    public ApiResponse<List<SalaryNoteResponse>> getSalaryNotesByContract(@PathVariable Long contractId) {
        List<SalaryNoteResponse> responses = salaryNoteService.getSalaryNotesByContractId(contractId);
        return ApiResponse.success(
                "Lấy danh sách ghi chú tiền lương thành công",
                responses,
                HttpStatus.OK.value());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('QLT1', 'QLT2', 'QLV')")
    public ApiResponse<SalaryNoteResponse> getSalaryNoteById(
            @PathVariable Long contractId,
            @PathVariable Long id) {
        SalaryNoteResponse response = salaryNoteService.getSalaryNoteById(id);
        return ApiResponse.success(
                "Lấy ghi chú tiền lương thành công",
                response,
                HttpStatus.OK.value());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('QLT1', 'QLT2', 'QLV')")
    public ApiResponse<SalaryNoteResponse> createSalaryNote(
            @PathVariable Long contractId,
            @Valid @RequestBody SalaryNoteRequest request) {
        request.setContractId(contractId);
        SalaryNoteResponse response = salaryNoteService.createSalaryNote(request);
        return ApiResponse.success(
                "Tạo ghi chú tiền lương thành công",
                response,
                HttpStatus.CREATED.value());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('QLT1', 'QLT2', 'QLV')")
    public ApiResponse<SalaryNoteResponse> updateSalaryNote(
            @PathVariable Long contractId,
            @PathVariable Long id,
            @Valid @RequestBody SalaryNoteRequest request) {
        request.setContractId(contractId);
        SalaryNoteResponse response = salaryNoteService.updateSalaryNote(id, request);
        return ApiResponse.success(
                "Cập nhật ghi chú tiền lương thành công",
                response,
                HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('QLT1', 'QLT2', 'QLV')")
    public ApiResponse<Void> deleteSalaryNote(
            @PathVariable Long contractId,
            @PathVariable Long id) {
        salaryNoteService.deleteSalaryNote(id);
        return ApiResponse.success(
                "Xóa ghi chú tiền lương thành công",
                null,
                HttpStatus.OK.value());
    }
}
