package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.entity.ServiceImage;
import java.util.List;
import java.util.Optional;

public interface ServiceImageService {
    List<ServiceImage> findAll();
    Optional<ServiceImage> findById(Long id);
    ServiceImage save(ServiceImage image);
    void deleteById(Long id);
}
