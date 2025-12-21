package com.company.company_clean_hub_be.dto.request;

import lombok.Data;

@Data
public class UpdateRatingRequest {
    private Integer rating;
    private String comment;
}
