package com.company.company_clean_hub_be.dto.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkScheduleCaptureRequest {
    
    @NotNull(message = "Work schedule ID is required")
    private Long workScheduleId;

    @NotBlank(message = "Image data is required")
    private String imageBase64;

    // GPS data
    private Double latitude;
    private Double longitude;
    private String address;

    // Image quality metrics
    private BigDecimal faceConfidence;
    private BigDecimal imageQualityScore;
}
