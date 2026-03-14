package com.company.company_clean_hub_be.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationImageResponse {
    
    private Long id;
    private Long verificationId;
    private Long employeeId;
    private Long attendanceId;
    
    private String cloudinaryPublicId;
    private String cloudinaryUrl;
    
    private Double latitude;
    private Double longitude;
    private String address;
    
    private LocalDateTime capturedAt;
    
    private BigDecimal faceConfidence;
    private BigDecimal imageQualityScore;
    
    private LocalDateTime createdAt;
}