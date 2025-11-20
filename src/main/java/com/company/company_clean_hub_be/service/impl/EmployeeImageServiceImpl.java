package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.entity.EmployeeImage;
import com.company.company_clean_hub_be.repository.EmployeeImageRepository;
import com.company.company_clean_hub_be.service.EmployeeImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class EmployeeImageServiceImpl implements EmployeeImageService {
    private final EmployeeImageRepository repository;

    @Override
    public List<EmployeeImage> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<EmployeeImage> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public EmployeeImage save(EmployeeImage image) {
        return repository.save(image);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
