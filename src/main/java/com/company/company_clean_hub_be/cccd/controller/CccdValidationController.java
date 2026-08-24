package com.company.company_clean_hub_be.cccd.controller;

import com.company.company_clean_hub_be.cccd.dto.CccdValidationResponse;
import com.company.company_clean_hub_be.cccd.service.impl.CccdValidationServiceImpl;
import com.company.company_clean_hub_be.dto.response.ApiResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@RestController
@RequestMapping({"/api/documents/cccd", "/api/v1/documents/cccd"})
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CccdValidationController {

    CccdValidationServiceImpl cccdValidationService;

    /**
     * POST /api/documents/cccd/validate & /api/v1/documents/cccd/validate
     * Nhận 2 ảnh CCCD (mặt trước + mặt sau), trả về kết quả validation & thông tin QR.
     */
    @PostMapping(value = "/validate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyAuthority('EMPLOYEE_CREATE', 'EMPLOYEE_EDIT')")
    public ApiResponse<CccdValidationResponse> validateCccd(
            @RequestPart("frontImage") MultipartFile frontImage,
            @RequestPart("backImage")  MultipartFile backImage) {

        log.info("CccdValidationController.validateCccd - Received request: frontName={}, size={}, backName={}, size={}",
                frontImage.getOriginalFilename(), frontImage.getSize(),
                backImage.getOriginalFilename(), backImage.getSize());

        CccdValidationResponse response = cccdValidationService.validate(frontImage, backImage);
        log.info("CccdValidationController.validateCccd - Completed validation: status={}, overallScore={}, hasExtractedData={}",
                response.getStatus(), response.getOverallScore(), response.getExtractedData() != null);

        return ApiResponse.success("Kiểm tra ảnh CCCD thành công", response, HttpStatus.OK.value());
    }
}
