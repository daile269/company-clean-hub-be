package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.entity.Contract;
import java.util.List;
import java.util.Optional;

public interface ContractService {
    List<Contract> findAll();
    Optional<Contract> findById(Long id);
    Contract save(Contract contract);
    void deleteById(Long id);
}
