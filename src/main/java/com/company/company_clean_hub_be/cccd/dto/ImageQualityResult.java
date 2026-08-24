package com.company.company_clean_hub_be.cccd.dto;

import com.company.company_clean_hub_be.cccd.enums.ValidationErrorCode;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ImageQualityResult {
    private boolean passed;
    private int qualityScore;         // 0-100
    private boolean blurry;
    private boolean tooDark;
    private boolean tooBright;
    private boolean lowContrast;
    private boolean tooSmall;
    private int width;
    private int height;
    private double blurVariance;
    private double brightness;
    private double contrast;
    private List<ValidationErrorCode> errors;
}
