package com.company.company_clean_hub_be.dto.response;

import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class RatingResponse {
    private Long id;
    private Long contractId;
    private Long assignmentId;
    private Long employeeId;
    private String employeeName;
    private String customerName;
    private String contractDescription;
    private String employeeCode;
    private Integer rating;
    private String comment;
    private String createdBy;
    private LocalDateTime createdAt;
    private Long reviewerId;
    private String reviewerName;
    private String reviewerRole;
}
