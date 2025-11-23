package com.company.company_clean_hub_be.service;

import com.company.company_clean_hub_be.dto.request.CustomerRequest;
import com.company.company_clean_hub_be.dto.response.CustomerResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;

import java.util.List;

public interface CustomerService {
    List<CustomerResponse> getAllCustomers();
    PageResponse<CustomerResponse> getCustomersWithFilter(String keyword, int page, int pageSize);
    CustomerResponse getCustomerById(Long id);
    CustomerResponse createCustomer(CustomerRequest request);
    CustomerResponse updateCustomer(Long id, CustomerRequest request);
    void deleteCustomer(Long id);
}
