package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeImageResponse {
    private Long id;
    private Long employeeId;
    private String cloudinaryPublicId;
    private String cloudinaryUrl;
    private LocalDateTime uploadedAt;
}
