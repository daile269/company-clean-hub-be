package com.company.company_clean_hub_be.cccd.dto;

import com.company.company_clean_hub_be.cccd.enums.ValidationErrorCode;
import com.company.company_clean_hub_be.cccd.enums.ValidationStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CccdValidationResponse {
    private boolean valid;
    private String documentType;        // "CCCD"
    private ValidationStatus status;    // VALID / REVIEW / INVALID

    private CccdSideResult front;
    private CccdSideResult back;

    private int overallScore;           // 0-100

    private CccdExtractedData extractedData; // Thông tin đọc từ QR Code (Số CCCD, Họ tên...)

    private String errorMessage;         // Lời nhắn báo lỗi chi tiết từ GPT AI Vision

    private List<ValidationErrorCode> errors;
}
