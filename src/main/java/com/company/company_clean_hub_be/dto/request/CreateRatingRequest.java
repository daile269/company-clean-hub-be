package com.company.company_clean_hub_be.dto.request;

import lombok.Data;

@Data
public class CreateRatingRequest {
    private Long contractId;
    private Long assignmentId;
    private Long employeeId;
    private Integer rating;
    private String comment;
    private String createdBy;
}
