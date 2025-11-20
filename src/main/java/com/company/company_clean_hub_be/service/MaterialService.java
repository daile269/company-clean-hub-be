package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.entity.Material;
import java.util.List;
import java.util.Optional;

public interface MaterialService {
    List<Material> findAll();
    Optional<Material> findById(Long id);
    Material save(Material material);
    void deleteById(Long id);
}
