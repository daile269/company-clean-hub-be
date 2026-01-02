package com.company.company_clean_hub_be.service.impl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.company.company_clean_hub_be.dto.request.CustomerRequest;
import com.company.company_clean_hub_be.dto.response.ContractDetailDto;
import com.company.company_clean_hub_be.dto.response.CustomerContractGroupDto;
import com.company.company_clean_hub_be.dto.response.CustomerContractServiceFlatDto;
import com.company.company_clean_hub_be.dto.response.CustomerResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.entity.Customer;
import com.company.company_clean_hub_be.entity.Role;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.ContractRepository;
import com.company.company_clean_hub_be.repository.CustomerRepository;
import com.company.company_clean_hub_be.repository.RoleRepository;
import com.company.company_clean_hub_be.service.CustomerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class CustomerServiceImpl implements CustomerService {
    private final CustomerRepository customerRepository;
    private final ContractRepository contractRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public String generateCustomerCode() {
        Pageable pageable = PageRequest.of(0, 1);
        List<String> existingCodes = customerRepository.findTopByCustomerCodeStartingWithKH(pageable);
        
        int nextNumber = 1;
        if (!existingCodes.isEmpty()) {
            String lastCode = existingCodes.get(0);
            try {
                String numberPart = lastCode.substring(2); // Bỏ "KH"
                nextNumber = Integer.parseInt(numberPart) + 1;
            } catch (Exception e) {
                log.warn("Cannot parse customer code: {}", lastCode);
            }
        }
        
        String generatedCode = "KH" + String.format("%06d", nextNumber);
        log.info("Generated customer code: {}", generatedCode);
        return generatedCode;
    }

    @Override
    public List<CustomerContractGroupDto> getCustomersWithContractsForExport() {
        log.info("getCustomersWithContractsForExport requested");
        
        List<CustomerContractServiceFlatDto> flatData = contractRepository.findAllCustomerContractServicesFlat();
        
        Map<Long, CustomerContractGroupDto> customerMap = new LinkedHashMap<>();
        Map<Long, ContractDetailDto> contractMap = new HashMap<>();
        Map<Long, List<BigDecimal>> contractVatMap = new HashMap<>();
        
        for (CustomerContractServiceFlatDto row : flatData) {
            Long customerId = row.getCustomerId();
            Long contractId = row.getContractId();
            
            customerMap.putIfAbsent(customerId, CustomerContractGroupDto.builder()
                    .customerId(customerId)
                    .customerName(row.getCustomerName())
                    .address(row.getAddress())
                    .taxCode(row.getTaxCode())
                    .email(row.getEmail())
                    .contracts(new ArrayList<>())
                    .build());
            
            if (!contractMap.containsKey(contractId)) {
                Integer workDaysValue = row.getWorkDays();
                String workingDaysStr = workDaysValue != null && workDaysValue.compareTo(0) > 0
                    ? formatWorkingDays(workDaysValue)
                    : "";
                int workDaysInt = workDaysValue != null ? workDaysValue : 0;
                
                ContractDetailDto contract = ContractDetailDto.builder()
                        .contractId(contractId)
                        .contractCode(String.valueOf(contractId))
                        .startDate(row.getStartDate() != null ? formatDate(row.getStartDate()) : "")
                        .endDate(row.getEndDate() != null ? formatDate(row.getEndDate()) : "")
                        .workingDays(workingDaysStr)
                        .workDays(workDaysInt)
                        .contractType(row.getContractType() != null ? row.getContractType() : "")
                        .paymentStatus(row.getPaymentStatus() != null ? row.getPaymentStatus() : "")
                        .description(row.getContractDescription() != null ? row.getContractDescription() : "")
                        .vatAmount(0.0)
                        .build();
                
                contractMap.put(contractId, contract);
                contractVatMap.put(contractId, new ArrayList<>());
            }
            
            if (row.getServiceId() != null && row.getServicePrice() != null && row.getVatPercent() != null) {
                BigDecimal vatAmount = row.getServicePrice()
                        .multiply(row.getVatPercent())
                        .divide(new BigDecimal("100"));
                contractVatMap.get(contractId).add(vatAmount);
            }
        }
        
        for (Map.Entry<Long, ContractDetailDto> entry : contractMap.entrySet()) {
            Long contractId = entry.getKey();
            ContractDetailDto contract = entry.getValue();
            
            BigDecimal totalVat = contractVatMap.get(contractId).stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            
            contract.setVatAmount(totalVat.doubleValue());
//            contract.setTotalValue(contract.getContractValue() + totalVat.doubleValue());
        }
        
        for (CustomerContractGroupDto customer : customerMap.values()) {
            customer.setContracts(contractMap.values().stream()
                    .filter(c -> flatData.stream()
                            .anyMatch(f -> f.getCustomerId().equals(customer.getCustomerId()) && 
                                          f.getContractId().equals(c.getContractId())))
                    .collect(Collectors.toList()));
        }
        
        log.info("getCustomersWithContractsForExport completed: total customers={}", customerMap.size());
        return new ArrayList<>(customerMap.values());
    }
    
    private String formatDate(String dateStr) {
        try {
            DateTimeFormatter inputFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            DateTimeFormatter outputFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            return LocalDate.parse(dateStr, inputFormatter).format(outputFormatter);
        } catch (Exception e) {
            return dateStr;
        }
    }
    
    private String formatWorkingDays(Integer workDays) {
        return workDays != null && workDays > 0 ? workDays + " ngày" : "";
    }

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
