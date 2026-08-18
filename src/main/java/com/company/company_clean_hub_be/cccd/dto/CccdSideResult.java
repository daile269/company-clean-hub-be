package com.company.company_clean_hub_be.cccd.dto;

import com.company.company_clean_hub_be.cccd.enums.DocumentSide;
import com.company.company_clean_hub_be.cccd.enums.ValidationErrorCode;
import com.company.company_clean_hub_be.cccd.enums.ValidationStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder(toBuilder = true)
public class CccdSideResult {
    private boolean valid;
    private DocumentSide side;
    private ValidationStatus status;

    // Quality
    private int qualityScore;         // 0-100
    private boolean blurry;
    private boolean cardDetected;

    // Template matching
    private int templateScore;        // 0-100

    // Scoring breakdown
    private int overallScore;         // weighted score tổng hợp 0-100

    private List<ValidationErrorCode> errors;
}
