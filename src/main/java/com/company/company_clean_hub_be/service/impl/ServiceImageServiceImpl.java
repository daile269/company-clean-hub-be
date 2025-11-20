package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.entity.ServiceImage;
import com.company.company_clean_hub_be.repository.ServiceImageRepository;
import com.company.company_clean_hub_be.service.ServiceImageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceImageServiceImpl implements ServiceImageService {
    private final ServiceImageRepository repository;

    @Override
    public List<ServiceImage> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<ServiceImage> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public ServiceImage save(ServiceImage image) {
        return repository.save(image);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
