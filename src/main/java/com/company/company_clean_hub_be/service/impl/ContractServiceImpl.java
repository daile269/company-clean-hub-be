package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.repository.ContractRepository;
import com.company.company_clean_hub_be.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ContractServiceImpl implements ContractService {
    private final ContractRepository repository;

    @Override
    public List<Contract> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<Contract> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Contract save(Contract contract) {
        return repository.save(contract);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
