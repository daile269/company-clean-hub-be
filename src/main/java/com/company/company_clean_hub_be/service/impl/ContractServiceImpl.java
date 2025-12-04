package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.ContractRequest;
import com.company.company_clean_hub_be.dto.response.ContractResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.ServiceResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.Customer;
import com.company.company_clean_hub_be.entity.ServiceEntity;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
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
    private final AssignmentRepository assignmentRepository;

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

        // Tự động tính tổng giá trị hợp đồng dựa trên các dịch vụ (bao gồm VAT)
        java.math.BigDecimal calculatedTotal = calculateTotalFromServices(services);

        Contract contract = Contract.builder()
                .customer(customer)
                .services(services)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .workingDaysPerWeek(request.getWorkingDaysPerWeek())
                .contractType(request.getContractType())
                .finalPrice(calculatedTotal)
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

        // Tự động tính lại tổng giá trị hợp đồng dựa trên các dịch vụ (bao gồm VAT)
        java.math.BigDecimal calculatedTotal = calculateTotalFromServices(services);

        contract.setCustomer(customer);
        contract.setServices(services);
        contract.setStartDate(request.getStartDate());
        contract.setEndDate(request.getEndDate());
        contract.setWorkingDaysPerWeek(request.getWorkingDaysPerWeek());
        contract.setContractType(request.getContractType());
        contract.setFinalPrice(calculatedTotal);
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

    @Override
    public ContractResponse addServiceToContract(Long contractId, Long serviceId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        
        ServiceEntity service = serviceEntityRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        
        contract.getServices().add(service);
        
        // Tự động tính lại tổng giá trị hợp đồng sau khi thêm dịch vụ
        java.math.BigDecimal calculatedTotal = calculateTotalFromServices(contract.getServices());
        contract.setFinalPrice(calculatedTotal);
        contract.setUpdatedAt(LocalDateTime.now());
        
        Contract updatedContract = contractRepository.save(contract);
        return mapToResponse(updatedContract);
    }

    @Override
    public ContractResponse removeServiceFromContract(Long contractId, Long serviceId) {
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        
        ServiceEntity service = serviceEntityRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        
        contract.getServices().remove(service);
        
        // Tự động tính lại tổng giá trị hợp đồng sau khi xóa dịch vụ
        java.math.BigDecimal calculatedTotal = calculateTotalFromServices(contract.getServices());
        contract.setFinalPrice(calculatedTotal);
        contract.setUpdatedAt(LocalDateTime.now());
        
        Contract updatedContract = contractRepository.save(contract);
        return mapToResponse(updatedContract);
    }

    private ContractResponse mapToResponse(Contract contract) {
        List<ServiceResponse> services = contract.getServices().stream()
                .map(service -> ServiceResponse.builder()
                        .id(service.getId())
                        .title(service.getTitle())
                        .description(service.getDescription())
                        .price(service.getPrice())
                        .vat(service.getVat())
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
                .workingDaysPerWeek(contract.getWorkingDaysPerWeek())
                .contractType(contract.getContractType())
                .finalPrice(contract.getFinalPrice())
                .paymentStatus(contract.getPaymentStatus())
                .description(contract.getDescription())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }

    private java.math.BigDecimal calculateTotalFromServices(Set<ServiceEntity> services) {
        return services.stream()
                .map(service -> {
                    java.math.BigDecimal price = service.getPrice() != null ? service.getPrice() : java.math.BigDecimal.ZERO;
                    java.math.BigDecimal vat = service.getVat() != null ? service.getVat() : java.math.BigDecimal.ZERO;
                    // Giá phải trả = Giá dịch vụ + (Giá dịch vụ × VAT / 100)
                    java.math.BigDecimal vatAmount = price.multiply(vat).divide(new java.math.BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
                    return price.add(vatAmount);
                })
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
    }

    @Override
    public ContractResponse getContractByAssignmentId(Long assignmentId) {
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        
        Contract contract = assignment.getContract();
        if (contract == null) {
            throw new AppException(ErrorCode.CONTRACT_NOT_FOUND);
        }
        
        return mapToResponse(contract);
    }
}
