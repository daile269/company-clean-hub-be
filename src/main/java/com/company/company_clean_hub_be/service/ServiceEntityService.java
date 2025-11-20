package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.entity.ServiceEntity;
import java.util.List;
import java.util.Optional;

public interface ServiceEntityService {
    List<ServiceEntity> findAll();
    Optional<ServiceEntity> findById(Long id);
    ServiceEntity save(ServiceEntity serviceEntity);
    void deleteById(Long id);
}
