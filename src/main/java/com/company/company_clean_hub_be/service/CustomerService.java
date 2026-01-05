package com.company.company_clean_hub_be.service;

import java.util.List;

import com.company.company_clean_hub_be.dto.request.CustomerRequest;
import com.company.company_clean_hub_be.dto.response.CustomerContractGroupDto;
import com.company.company_clean_hub_be.dto.response.CustomerResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;

public interface CustomerService {
    List<CustomerResponse> getAllCustomers();
    PageResponse<CustomerResponse> getCustomersWithFilter(String keyword, int page, int pageSize);
    CustomerResponse getCustomerById(Long id);
    CustomerResponse createCustomer(CustomerRequest request);
    CustomerResponse updateCustomer(Long id, CustomerRequest request);
    void deleteCustomer(Long id);
    List<CustomerContractGroupDto> getCustomersWithContractsForExport();
    
    // Phương thức sinh mã khách hàng tự động
    String generateCustomerCode();
}
