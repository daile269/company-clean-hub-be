package com.company.company_clean_hub_be.dto.response;

import java.time.LocalDate;
import java.util.List;

import com.company.company_clean_hub_be.entity.ContractType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignmentsByContractResponse {
    private Long contractId;
    private String contractDescription;
    private LocalDate contractStartDate;
    private ContractType contractType;
    private List<String> serviceNames;
    private List<AssignmentResponse> assignments;
}
