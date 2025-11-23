package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.ServiceRequest;
import com.company.company_clean_hub_be.dto.response.ServiceResponse;
import com.company.company_clean_hub_be.entity.ServiceEntity;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.ServiceEntityRepository;
import com.company.company_clean_hub_be.service.ServiceEntityService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ServiceEntityServiceImpl implements ServiceEntityService {
    private final ServiceEntityRepository serviceEntityRepository;

    @Override
    public List<ServiceResponse> getAllServices() {
        return serviceEntityRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ServiceResponse getServiceById(Long id) {
        ServiceEntity service = serviceEntityRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        return mapToResponse(service);
    }

    @Override
    public ServiceResponse createService(ServiceRequest request) {
        ServiceEntity service = ServiceEntity.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .priceFrom(request.getPriceFrom())
                .priceTo(request.getPriceTo())
                .mainImage(request.getMainImage())
                .status(request.getStatus())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ServiceEntity savedService = serviceEntityRepository.save(service);
        return mapToResponse(savedService);
    }

    @Override
    public ServiceResponse updateService(Long id, ServiceRequest request) {
        ServiceEntity service = serviceEntityRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));

        service.setTitle(request.getTitle());
        service.setDescription(request.getDescription());
        service.setPriceFrom(request.getPriceFrom());
        service.setPriceTo(request.getPriceTo());
        service.setMainImage(request.getMainImage());
        service.setStatus(request.getStatus());
        service.setUpdatedAt(LocalDateTime.now());

        ServiceEntity updatedService = serviceEntityRepository.save(service);
        return mapToResponse(updatedService);
    }

    @Override
    public void deleteService(Long id) {
        ServiceEntity service = serviceEntityRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        serviceEntityRepository.delete(service);
    }

    private ServiceResponse mapToResponse(ServiceEntity service) {
        return ServiceResponse.builder()
                .id(service.getId())
                .title(service.getTitle())
                .description(service.getDescription())
                .priceFrom(service.getPriceFrom())
                .priceTo(service.getPriceTo())
                .mainImage(service.getMainImage())
                .status(service.getStatus())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }
}
