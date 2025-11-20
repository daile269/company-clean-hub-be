package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.entity.Material;
import com.company.company_clean_hub_be.repository.MaterialRepository;
import com.company.company_clean_hub_be.service.MaterialService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class MaterialServiceImpl implements MaterialService {
    private final MaterialRepository repository;

    @Override
    public List<Material> findAll() {
        return repository.findAll();
    }

    @Override
    public Optional<Material> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Material save(Material material) {
        return repository.save(material);
    }

    @Override
    public void deleteById(Long id) {
        repository.deleteById(id);
    }
}
