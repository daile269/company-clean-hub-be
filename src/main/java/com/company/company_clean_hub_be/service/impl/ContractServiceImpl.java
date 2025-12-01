package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.ContractRequest;
import com.company.company_clean_hub_be.dto.response.ContractResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.ServiceResponse;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.Customer;
import com.company.company_clean_hub_be.entity.ServiceEntity;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.ContractRepository;
import com.company.company_clean_hub_be.repository.CustomerRepository;
import com.company.company_clean_hub_be.repository.ServiceEntityRepository;
import com.company.company_clean_hub_be.service.ContractService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ContractServiceImpl implements ContractService {
    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final ServiceEntityRepository serviceEntityRepository;

    @Override
    public List<ContractResponse> getAllContracts() {
        return contractRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<ContractResponse> getContractsWithFilter(String keyword, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        Page<Contract> contractPage = contractRepository.findByFilters(keyword, pageable);

        List<ContractResponse> contracts = contractPage.getContent().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());

        return PageResponse.<ContractResponse>builder()
                .content(contracts)
                .page(contractPage.getNumber())
                .pageSize(contractPage.getSize())
                .totalElements(contractPage.getTotalElements())
                .totalPages(contractPage.getTotalPages())
                .first(contractPage.isFirst())
                .last(contractPage.isLast())
                .build();
    }

    @Override
    public ContractResponse getContractById(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        return mapToResponse(contract);
    }

    @Override
    public ContractResponse createContract(ContractRequest request) {
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

        Set<ServiceEntity> services = new HashSet<>();
        for (Long serviceId : request.getServiceIds()) {
            ServiceEntity service = serviceEntityRepository.findById(serviceId)
                    .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
            services.add(service);
        }

        Contract contract = Contract.builder()
                .customer(customer)
                .services(services)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .basePrice(request.getBasePrice())
                .vat(request.getVat())
                .total(request.getTotal())
                .extraCost(request.getExtraCost())
                .discountCost(request.getDiscountCost())
                .finalPrice(request.getFinalPrice())
                .paymentStatus(request.getPaymentStatus())
                .description(request.getDescription())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Contract savedContract = contractRepository.save(contract);
        return mapToResponse(savedContract);
    }

    @Override
    public ContractResponse updateContract(Long id, ContractRequest request) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

        Set<ServiceEntity> services = new HashSet<>();
        for (Long serviceId : request.getServiceIds()) {
            ServiceEntity service = serviceEntityRepository.findById(serviceId)
                    .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
            services.add(service);
        }

        contract.setCustomer(customer);
        contract.setServices(services);
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setBasePrice(request.getBasePrice());
        contract.setVat(request.getVat());
        contract.setTotal(request.getTotal());
        contract.setExtraCost(request.getExtraCost());
        contract.setDiscountCost(request.getDiscountCost());
        contract.setFinalPrice(request.getFinalPrice());
        contract.setPaymentStatus(request.getPaymentStatus());
        contract.setDescription(request.getDescription());
        contract.setUpdatedAt(LocalDateTime.now());

        Contract updatedContract = contractRepository.save(contract);
        return mapToResponse(updatedContract);
    }

    @Override
    public void deleteContract(Long id) {
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        contractRepository.delete(contract);
    }

    @Override
    public List<ContractResponse> getContractsByCustomer(Long customerId) {
        customerRepository.findById(customerId)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));
        
        List<Contract> contracts = contractRepository.findByCustomerId(customerId);
        
        return contracts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private ContractResponse mapToResponse(Contract contract) {
        List<ServiceResponse> services = contract.getServices().stream()
                .map(service -> ServiceResponse.builder()
                        .id(service.getId())
                        .title(service.getTitle())
                        .description(service.getDescription())
                        .price(service.getPrice())
                        .createdAt(service.getCreatedAt())
                        .updatedAt(service.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

        return ContractResponse.builder()
                .id(contract.getId())
                .customerId(contract.getCustomer().getId())
                .customerName(contract.getCustomer().getName())
                .services(services)
                .startDate(contract.getStartDate())
                .endDate(contract.getEndDate())
                .basePrice(contract.getBasePrice())
                .vat(contract.getVat())
                .total(contract.getTotal())
                .extraCost(contract.getExtraCost())
                .discountCost(contract.getDiscountCost())
                .finalPrice(contract.getFinalPrice())
                .paymentStatus(contract.getPaymentStatus())
                .description(contract.getDescription())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }
}
