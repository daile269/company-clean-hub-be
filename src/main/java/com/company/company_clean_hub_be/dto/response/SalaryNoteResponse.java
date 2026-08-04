package com.company.company_clean_hub_be.dto.response;

import com.company.company_clean_hub_be.entity.SalaryNoteCategory;
import com.company.company_clean_hub_be.entity.SalaryNoteType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SalaryNoteResponse {
    private Long id;
    private Long contractId;
    private String contractDescription;
    private SalaryNoteCategory category;
    private SalaryNoteType salaryType;
    private BigDecimal amount;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
