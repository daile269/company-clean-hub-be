package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.entity.MaterialDistribution;
import com.company.company_clean_hub_be.repository.MaterialDistributionRepository;
import com.company.company_clean_hub_be.service.MaterialDistributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class MaterialDistributionServiceImpl implements MaterialDistributionService {
    private final MaterialDistributionRepository repository;

    @Override
    public List<MaterialDistribution> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<MaterialDistribution> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public MaterialDistribution save(MaterialDistribution materialDistribution) {
        return repository.save(materialDistribution);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
