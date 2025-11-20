package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.entity.ServiceEntity;
import com.company.company_clean_hub_be.repository.ServiceEntityRepository;
import com.company.company_clean_hub_be.service.ServiceEntityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceEntityServiceImpl implements ServiceEntityService {
    private final ServiceEntityRepository repository;

    @Override
    public List<ServiceEntity> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<ServiceEntity> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public ServiceEntity save(ServiceEntity serviceEntity) {
        return repository.save(serviceEntity);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
