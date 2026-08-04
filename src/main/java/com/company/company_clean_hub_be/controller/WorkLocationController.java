package com.company.company_clean_hub_be.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import com.company.company_clean_hub_be.dto.request.WorkLocationRequest;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import com.company.company_clean_hub_be.dto.response.WorkLocationResponse;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.WorkLocation;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.ContractRepository;
import com.company.company_clean_hub_be.repository.WorkLocationRepository;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.AccessLevel;

@RestController
@RequestMapping("/api/contracts/{contractId}/work-locations")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class WorkLocationController {

    WorkLocationRepository workLocationRepository;
    ContractRepository contractRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('QLT1', 'QLT2', 'QLV')")
    public ApiResponse<List<WorkLocationResponse>> getWorkLocations(@PathVariable Long contractId) {
        List<WorkLocation> locations = workLocationRepository.findByContractId(contractId);
        List<WorkLocationResponse> result = locations.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ApiResponse.success("Lấy danh sách vị trí làm việc thành công", result, HttpStatus.OK.value());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('QLT1', 'QLT2', 'QLV')")
    public ApiResponse<WorkLocationResponse> createWorkLocation(
            @PathVariable Long contractId,
            @Valid @RequestBody WorkLocationRequest request) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        WorkLocation location = WorkLocation.builder()
                .contract(contract)
                .name(request.getName())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .radiusMeters(request.getRadiusMeters())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        WorkLocation saved = workLocationRepository.save(location);
        return ApiResponse.success("Tạo vị trí làm việc thành công", mapToResponse(saved), HttpStatus.CREATED.value());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('QLT1', 'QLT2', 'QLV')")
    public ApiResponse<WorkLocationResponse> updateWorkLocation(
            @PathVariable Long contractId,
            @PathVariable Long id,
            @Valid @RequestBody WorkLocationRequest request) {
        WorkLocation location = workLocationRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.NOTIFICATION_NOT_FOUND));

        location.setName(request.getName());
        location.setLatitude(request.getLatitude());
        location.setLongitude(request.getLongitude());
        location.setRadiusMeters(request.getRadiusMeters());
        location.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        location.setUpdatedAt(LocalDateTime.now());

        WorkLocation saved = workLocationRepository.save(location);
        return ApiResponse.success("Cập nhật vị trí làm việc thành công", mapToResponse(saved), HttpStatus.OK.value());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('QLT1', 'QLT2', 'QLV')")
    public ApiResponse<Void> deleteWorkLocation(@PathVariable Long contractId, @PathVariable Long id) {
        if (!workLocationRepository.existsById(id)) {
            throw new AppException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
        workLocationRepository.deleteById(id);
        return ApiResponse.success("Xóa vị trí làm việc thành công", null, HttpStatus.OK.value());
    }

    private WorkLocationResponse mapToResponse(WorkLocation location) {
        return WorkLocationResponse.builder()
                .id(location.getId())
                .contractId(location.getContract() != null ? location.getContract().getId() : null)
                .name(location.getName())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .radiusMeters(location.getRadiusMeters())
                .isActive(location.getIsActive())
                .createdAt(location.getCreatedAt())
                .updatedAt(location.getUpdatedAt())
                .build();
    }
}
