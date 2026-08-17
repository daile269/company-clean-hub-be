package com.company.company_clean_hub_be.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReassignmentHistoryByContractResponse {
    private Long contractId;
    private String contractDescription;
    private List<String> serviceNames;
    private List<AssignmentHistoryResponse> histories;
    // pagination for contracts is provided by PageResponse; per-contract metadata removed
}
