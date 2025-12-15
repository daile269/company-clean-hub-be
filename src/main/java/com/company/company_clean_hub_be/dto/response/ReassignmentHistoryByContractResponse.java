package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReassignmentHistoryByContractResponse {
    private Long contractId;
    private String contractDescription;
    private List<AssignmentHistoryResponse> histories;
    // pagination for contracts is provided by PageResponse; per-contract metadata removed
}
