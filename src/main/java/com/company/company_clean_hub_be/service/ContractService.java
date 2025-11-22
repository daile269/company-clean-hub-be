package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.ContractRequest;
import com.company.company_clean_hub_be.dto.response.ContractResponse;
import java.util.List;

public interface ContractService {
    List<ContractResponse> getAllContracts();
    ContractResponse getContractById(Long id);
    ContractResponse createContract(ContractRequest request);
    ContractResponse updateContract(Long id, ContractRequest request);
    void deleteContract(Long id);
}
