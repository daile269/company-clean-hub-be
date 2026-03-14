package com.company.company_clean_hub_be.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationCaptureRequest {
    
    @NotNull(message = "Verification ID is required")
    private Long verificationId;

    @NotBlank(message = "Image data is required")
    private String imageData; // Base64 string from frontend

    private Long attendanceId; // Optional: link to specific attendance

    // GPS and location data
    private Double latitude;
    private Double longitude;
    private String address;

    // Image quality metrics from frontend
    private BigDecimal faceConfidence;
    private BigDecimal imageQualityScore;
}