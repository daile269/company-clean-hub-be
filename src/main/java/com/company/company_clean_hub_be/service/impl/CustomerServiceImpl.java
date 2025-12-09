package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.CustomerRequest;
import com.company.company_clean_hub_be.dto.response.CustomerResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.entity.Customer;
import com.company.company_clean_hub_be.entity.Role;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.CustomerRepository;
import com.company.company_clean_hub_be.repository.RoleRepository;
import com.company.company_clean_hub_be.service.CustomerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CustomerServiceImpl implements CustomerService {
    private final CustomerRepository customerRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public List<CustomerResponse> getAllCustomers() {
        return customerRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<CustomerResponse> getCustomersWithFilter(String keyword, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        Page<Customer> customerPage = customerRepository.findByFilters(keyword, pageable);

        List<CustomerResponse> customers = customerPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PageResponse.<CustomerResponse>builder()
                .content(customers)
                .page(customerPage.getNumber())
                .pageSize(customerPage.getSize())
                .totalElements(customerPage.getTotalElements())
                .totalPages(customerPage.getTotalPages())
                .first(customerPage.isFirst())
                .last(customerPage.isLast())
                .build();
    }

    @Override
    public CustomerResponse getCustomerById(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));
        log.info("getCustomerById requested: id={}", id);
        return mapToResponse(customer);
    }

    @Override
    public CustomerResponse createCustomer(CustomerRequest request) {
        String username = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication().getName();
        log.info("createCustomer by {}: username={}, customerCode={}", username, request.getUsername(), request.getCustomerCode());
        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        Customer customer = Customer.builder()
                .customerCode(request.getCustomerCode())
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .email(request.getEmail())
                .role(role)
                .status(request.getStatus())
                .name(request.getName())
                .address(request.getAddress())
                .contactInfo(request.getContactInfo())
                .taxCode(request.getTaxCode())
                .description(request.getDescription())
                .company(request.getCompany())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Customer savedCustomer = customerRepository.save(customer);
        log.info("createCustomer completed by {}: customerId={}", username, savedCustomer.getId());
        return mapToResponse(savedCustomer);
    }

    @Override
    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
        String username = org.springframework.security.core.context.SecurityContextHolder
            .getContext().getAuthentication().getName();
        log.info("updateCustomer by {}: id={}, username={}", username, id, request.getUsername());
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

        Role role = roleRepository.findById(request.getRoleId())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        customer.setCustomerCode(request.getCustomerCode());
        customer.setUsername(request.getUsername());
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            customer.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        customer.setPhone(request.getPhone());
        customer.setEmail(request.getEmail());
        customer.setRole(role);
        customer.setStatus(request.getStatus());
        customer.setName(request.getName());
        customer.setAddress(request.getAddress());
        customer.setContactInfo(request.getContactInfo());
        customer.setTaxCode(request.getTaxCode());
        customer.setDescription(request.getDescription());
        customer.setCompany(request.getCompany());
        customer.setUpdatedAt(LocalDateTime.now());

        Customer updatedCustomer = customerRepository.save(customer);
        log.info("updateCustomer completed by {}: id={}", username, updatedCustomer.getId());
        return mapToResponse(updatedCustomer);
    }

    @Override
    public void deleteCustomer(Long id) {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        log.info("deleteCustomer requested by {}: id={}", username, id);
        if (!customerRepository.existsById(id)) {
            throw new AppException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        customerRepository.deleteById(id);
        log.info("deleteCustomer completed: id={}", id);
    }

    private CustomerResponse mapToResponse(Customer customer) {
        return CustomerResponse.builder()
                .id(customer.getId())
                .customerCode(customer.getCustomerCode())
                .username(customer.getUsername())
                .phone(customer.getPhone())
                .email(customer.getEmail())
                .roleId(customer.getRole() != null ? customer.getRole().getId() : null)
                .roleName(customer.getRole() != null ? customer.getRole().getName() : null)
                .status(customer.getStatus())
                .name(customer.getName())
                .address(customer.getAddress())
                .contactInfo(customer.getContactInfo())
                .taxCode(customer.getTaxCode())
                .description(customer.getDescription())
                .company(customer.getCompany())
                .createdAt(customer.getCreatedAt())
                .updatedAt(customer.getUpdatedAt())
                .build();
    }
}
