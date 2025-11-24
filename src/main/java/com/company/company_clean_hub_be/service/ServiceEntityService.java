package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.ServiceRequest;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.ServiceResponse;
import java.util.List;

public interface ServiceEntityService {
    List<ServiceResponse> getAllServices();
    PageResponse<ServiceResponse> getServicesWithFilter(String keyword, int page, int pageSize);
    ServiceResponse getServiceById(Long id);
    ServiceResponse createService(ServiceRequest request);
    ServiceResponse updateService(Long id, ServiceRequest request);
    void deleteService(Long id);
}
