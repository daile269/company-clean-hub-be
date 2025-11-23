package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.entity.MaterialDistribution;
import java.util.List;
import java.util.Optional;

public interface MaterialDistributionService {
    List<MaterialDistribution> findAll();
    Optional<MaterialDistribution> findById(Long id);
    MaterialDistribution save(MaterialDistribution materialDistribution);
    void deleteById(Long id);
}
