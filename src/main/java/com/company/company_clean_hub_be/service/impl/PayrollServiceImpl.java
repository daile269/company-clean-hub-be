package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.entity.Payroll;
import com.company.company_clean_hub_be.repository.PayrollRepository;
import com.company.company_clean_hub_be.service.PayrollService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class PayrollServiceImpl implements PayrollService {
    private final PayrollRepository repository;

    @Override
    public List<Payroll> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<Payroll> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Payroll save(Payroll payroll) {
        return repository.save(payroll);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
