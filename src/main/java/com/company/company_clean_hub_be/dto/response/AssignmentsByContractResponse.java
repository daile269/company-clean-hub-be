package com.company.company_clean_hub_be.dto.response;

import com.company.company_clean_hub_be.entity.ContractType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentsByContractResponse {
    private Long contractId;
    private String contractDescription;
    private LocalDate contractStartDate;
    private ContractType contractType;
    private List<AssignmentResponse> assignments;
}
