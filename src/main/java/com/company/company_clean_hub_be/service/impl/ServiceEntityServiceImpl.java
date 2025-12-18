package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.ServiceRequest;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.ServiceResponse;
import com.company.company_clean_hub_be.entity.ServiceEntity;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.ServiceEntityRepository;
import com.company.company_clean_hub_be.service.ServiceEntityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ServiceEntityServiceImpl implements ServiceEntityService {
    private final ServiceEntityRepository serviceEntityRepository;

    @Override
    public List<ServiceResponse> getAllServices() {
        log.info("getAllServices requested");
        return serviceEntityRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<ServiceResponse> getServicesWithFilter(String keyword, int page, int pageSize) {
        log.info("getServicesWithFilter requested: keyword='{}', page={}, pageSize={}", keyword, page, pageSize);
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        Page<ServiceEntity> servicePage = serviceEntityRepository.findByFilters(keyword, pageable);

        List<ServiceResponse> services = servicePage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PageResponse.<ServiceResponse>builder()
                .content(services)
                .page(servicePage.getNumber())
                .pageSize(servicePage.getSize())
                .totalElements(servicePage.getTotalElements())
                .totalPages(servicePage.getTotalPages())
                .first(servicePage.isFirst())
                .last(servicePage.isLast())
                .build();
    }

    @Override
    public ServiceResponse getServiceById(Long id) {
        log.info("getServiceById requested: id={}", id);
        ServiceEntity service = serviceEntityRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        return mapToResponse(service);
    }

    @Override
    public ServiceResponse createService(ServiceRequest request) {
        String username = "anonymous";
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getName() != null) username = auth.getName();
        } catch (Exception ignored) {
        }
        log.info("createService requested by {}: title='{}', price={}, effectiveFrom={}, serviceType={}", 
                username, request.getTitle(), request.getPrice(), request.getEffectiveFrom(), request.getServiceType());
        ServiceEntity service = ServiceEntity.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .price(request.getPrice())
                .vat(request.getVat())
                .effectiveFrom(request.getEffectiveFrom())
                .serviceType(request.getServiceType())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        ServiceEntity savedService = serviceEntityRepository.save(service);
        log.info("createService completed by {}: id={}", username, savedService.getId());
        return mapToResponse(savedService);
    }

    @Override
    public ServiceResponse updateService(Long id, ServiceRequest request) {
        String username = "anonymous";
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getName() != null) username = auth.getName();
        } catch (Exception ignored) {
        }
        log.info("updateService requested by {}: id={}, title='{}'", username, id, request.getTitle());
        ServiceEntity service = serviceEntityRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));

        service.setTitle(request.getTitle());
        service.setDescription(request.getDescription());
        service.setPrice(request.getPrice());
        service.setVat(request.getVat());
        service.setEffectiveFrom(request.getEffectiveFrom());
        service.setServiceType(request.getServiceType());
        service.setUpdatedAt(LocalDateTime.now());

        ServiceEntity updatedService = serviceEntityRepository.save(service);

        // Pricing is now handled by invoices. Do not modify contract pricing here.
        log.info("updateService completed by {}: id={}", username, updatedService.getId());

        return mapToResponse(updatedService);
    }
    
    // Pricing moved to Invoice creation. No contract price updates here.

    @Override
    public void deleteService(Long id) {
        String username = "anonymous";
        try {
            org.springframework.security.core.Authentication auth = org.springframework.security.core.context.SecurityContextHolder
                    .getContext().getAuthentication();
            if (auth != null && auth.getName() != null) username = auth.getName();
        } catch (Exception ignored) {
        }
        log.info("deleteService requested by {}: id={}", username, id);
        ServiceEntity service = serviceEntityRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        serviceEntityRepository.delete(service);
        log.info("deleteService completed: id={}", id);
    }

    private ServiceResponse mapToResponse(ServiceEntity service) {
        return ServiceResponse.builder()
                .id(service.getId())
                .title(service.getTitle())
                .description(service.getDescription())
                .price(service.getPrice())
                .vat(service.getVat())
                .effectiveFrom(service.getEffectiveFrom())
                .serviceType(service.getServiceType())
                .createdAt(service.getCreatedAt())
                .updatedAt(service.getUpdatedAt())
                .build();
    }
}
