package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.ContractRequest;
import com.company.company_clean_hub_be.dto.response.ContractResponse;
import com.company.company_clean_hub_be.dto.response.PageResponse;
import com.company.company_clean_hub_be.dto.response.ServiceResponse;
import com.company.company_clean_hub_be.entity.Assignment;
import com.company.company_clean_hub_be.entity.Contract;
import com.company.company_clean_hub_be.entity.ContractType;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ContractServiceImpl implements ContractService {
    private final ContractRepository contractRepository;
    private final CustomerRepository customerRepository;
    private final ServiceEntityRepository serviceEntityRepository;
    private final AssignmentRepository assignmentRepository;

    @Override
    public List<ContractResponse> getAllContracts() {
                log.info("getAllContracts requested");
        return contractRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public PageResponse<ContractResponse> getContractsWithFilter(String keyword, int page, int pageSize) {
        log.info("getContractsWithFilter requested: keyword='{}', page={}, pageSize={}", keyword, page, pageSize);
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
        log.info("getContractById requested: id={}", id);
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        return mapToResponse(contract);
    }

    @Override
    public ContractResponse createContract(ContractRequest request) {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        log.info("createContract by {}: customerId={}, serviceCount={}", username, request.getCustomerId(),
                request.getServiceIds() != null ? request.getServiceIds().size() : 0);
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

        Set<ServiceEntity> services = new HashSet<>();
        for (Long serviceId : request.getServiceIds()) {
            ServiceEntity service = serviceEntityRepository.findById(serviceId)
                    .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
            services.add(service);
        }

        // Tính giá trị hợp đồng dựa trên loại hợp đồng
        java.math.BigDecimal calculatedTotal = calculatePriceByContractType(
                services,
                request.getContractType(),
                request.getWorkingDaysPerWeek(),
                request.getStartDate(),
                request.getEndDate(),
                null);

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
                log.info("createContract completed by {}: contractId={}", username, savedContract.getId());
        return mapToResponse(savedContract);
    }

    @Override
    public ContractResponse updateContract(Long id, ContractRequest request) {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        log.info("updateContract by {}: id={}, serviceCount={}", username, id,
                request.getServiceIds() != null ? request.getServiceIds().size() : 0);
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

        // Tính lại giá trị hợp đồng dựa trên loại hợp đồng
        java.math.BigDecimal calculatedTotal = calculatePriceByContractType(
                services,
                request.getContractType(),
                request.getWorkingDaysPerWeek(),
                request.getStartDate(),
                request.getEndDate(),
                contract.getId());

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
        log.info("updateContract completed by {}: id={}", username, updatedContract.getId());
        return mapToResponse(updatedContract);
    }

    @Override
    public void deleteContract(Long id) {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        log.info("deleteContract requested by {}: id={}", username, id);
        Contract contract = contractRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        contractRepository.delete(contract);
        log.info("deleteContract completed: id={}", id);
    }

    @Override
    public List<ContractResponse> getContractsByCustomer(Long customerId) {
        log.info("getContractsByCustomer requested: customerId={}", customerId);
        customerRepository.findById(customerId)
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));
        
        List<Contract> contracts = contractRepository.findByCustomerId(customerId);
        
        return contracts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public ContractResponse addServiceToContract(Long contractId, Long serviceId) {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        log.info("addServiceToContract by {}: contractId={}, serviceId={}", username, contractId, serviceId);
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        
        ServiceEntity service = serviceEntityRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        
        contract.getServices().add(service);
        
        // Tính lại giá trị hợp đồng sau khi thêm dịch vụ
        java.math.BigDecimal calculatedTotal = calculatePriceByContractType(
                contract.getServices(),
                contract.getContractType(),
                contract.getWorkingDaysPerWeek(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getId());
        contract.setFinalPrice(calculatedTotal);
        contract.setUpdatedAt(LocalDateTime.now());
        
        Contract updatedContract = contractRepository.save(contract);
        log.info("addServiceToContract completed: contractId={}, newFinalPrice={}", updatedContract.getId(), updatedContract.getFinalPrice());
        return mapToResponse(updatedContract);
    }

    @Override
    public ContractResponse removeServiceFromContract(Long contractId, Long serviceId) {
        String username = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getName();
        log.info("removeServiceFromContract by {}: contractId={}, serviceId={}", username, contractId, serviceId);
        Contract contract = contractRepository.findById(contractId)
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));
        
        ServiceEntity service = serviceEntityRepository.findById(serviceId)
                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));
        
        contract.getServices().remove(service);
        
        // Tính lại giá trị hợp đồng sau khi xóa dịch vụ
        java.math.BigDecimal calculatedTotal = calculatePriceByContractType(
                contract.getServices(),
                contract.getContractType(),
                contract.getWorkingDaysPerWeek(),
                contract.getStartDate(),
                contract.getEndDate(),
                contract.getId());
        contract.setFinalPrice(calculatedTotal);
        contract.setUpdatedAt(LocalDateTime.now());
        
        Contract updatedContract = contractRepository.save(contract);
        log.info("removeServiceFromContract completed: contractId={}, newFinalPrice={}", updatedContract.getId(), updatedContract.getFinalPrice());
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
                        .effectiveFrom(service.getEffectiveFrom())
                        .serviceType(service.getServiceType())
                        .createdAt(service.getCreatedAt())
                        .updatedAt(service.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

                // compute plannedDays for month of startDate (if possible)
                Integer plannedDays = null;
                if (contract.getWorkingDaysPerWeek() != null && !contract.getWorkingDaysPerWeek().isEmpty()
                                && contract.getStartDate() != null) {
                        java.time.YearMonth ym = java.time.YearMonth.from(contract.getStartDate());
                        java.time.LocalDate start = contract.getStartDate();
                        java.time.LocalDate end = ym.atEndOfMonth();
                        if (contract.getEndDate() != null && contract.getEndDate().isBefore(end)) {
                                end = contract.getEndDate();
                        }
                        int count = 0;
                        java.time.LocalDate cur = start;
                        while (!cur.isAfter(end)) {
                                if (contract.getWorkingDaysPerWeek().contains(cur.getDayOfWeek())) {
                                        count++;
                                }
                                cur = cur.plusDays(1);
                        }
                        plannedDays = count;
                }

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
                                .plannedDays(plannedDays)
                .paymentStatus(contract.getPaymentStatus())
                .description(contract.getDescription())
                .createdAt(contract.getCreatedAt())
                .updatedAt(contract.getUpdatedAt())
                .build();
    }

    /**
     * Tính giá hợp đồng dựa trên loại hợp đồng:
     * - MONTHLY_ACTUAL: giá ngày × số ngày làm việc trong tháng (dựa vào workingDaysPerWeek)
     * - ONE_TIME, MONTHLY_FIXED: tổng giá cố định
     */
    private java.math.BigDecimal calculatePriceByContractType(
            Set<ServiceEntity> services,
            ContractType contractType,
            List<java.time.DayOfWeek> workingDaysPerWeek,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            Long contractId) {
        
        java.math.BigDecimal dailyRate = calculateTotalFromServices(services);
        
        // Với MONTHLY_ACTUAL: giá ngày × số ngày làm việc trong tháng
                if (contractType == ContractType.MONTHLY_ACTUAL) {
                        int workDaysCount = 0;

                        if (workingDaysPerWeek != null && !workingDaysPerWeek.isEmpty() && startDate != null) {
                                // compute for month of startDate
                                java.time.YearMonth yearMonth = java.time.YearMonth.from(startDate);
                                java.time.LocalDate monthEnd = yearMonth.atEndOfMonth();
                                if (endDate != null && endDate.isBefore(monthEnd)) {
                                        monthEnd = endDate;
                                }
                                java.time.LocalDate currentDate = startDate;
                                while (!currentDate.isAfter(monthEnd)) {
                                        if (workingDaysPerWeek.contains(currentDate.getDayOfWeek())) {
                                                workDaysCount++;
                                        }
                                        currentDate = currentDate.plusDays(1);
                                }
                        } else if (contractId != null) {
                                // Fallback: sum plannedDays of assignments for this contract (month of startDate if available)
                                List<Assignment> assignments = assignmentRepository.findByContractId(contractId);
                                if (assignments != null && !assignments.isEmpty()) {
                                        if (startDate != null) {
                                                java.time.YearMonth ym = java.time.YearMonth.from(startDate);
                                                for (Assignment a : assignments) {
                                                        if (a.getPlannedDays() != null) {
                                                                workDaysCount += a.getPlannedDays();
                                                        } else if (a.getWorkingDaysPerWeek() != null && a.getStartDate() != null) {
                                                                // compute planned days for this assignment within the month
                                                                java.time.LocalDate start = a.getStartDate();
                                                                java.time.LocalDate monthStart = ym.atDay(1);
                                                                java.time.LocalDate monthEnd = ym.atEndOfMonth();
                                                                java.time.LocalDate as = start.isBefore(monthStart) ? monthStart : start;
                                                                java.time.LocalDate ae = a.getStartDate() != null && a.getStartDate().isAfter(monthEnd) ? monthEnd : monthEnd;
                                                                java.time.LocalDate cur = as;
                                                                while (!cur.isAfter(ae)) {
                                                                        if (a.getWorkingDaysPerWeek().contains(cur.getDayOfWeek())) {
                                                                                workDaysCount++;
                                                                        }
                                                                        cur = cur.plusDays(1);
                                                                }
                                                        }
                                                }
                                        } else {
                                                // no startDate: sum all assignment.plannedDays if available
                                                for (Assignment a : assignments) {
                                                        if (a.getPlannedDays() != null) workDaysCount += a.getPlannedDays();
                                                }
                                        }
                                }
                        }

                        if (workDaysCount <= 0) {
                                // if still unknown, return dailyRate (best-effort)
                                return dailyRate;
                        }

                        return dailyRate.multiply(java.math.BigDecimal.valueOf(workDaysCount));
                }
        
        // Với ONE_TIME và MONTHLY_FIXED, giá là tổng giá cố định
        return dailyRate;
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
        log.info("getContractByAssignmentId requested: assignmentId={}", assignmentId);
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new AppException(ErrorCode.ASSIGNMENT_NOT_FOUND));
        
        Contract contract = assignment.getContract();
        if (contract == null) {
            throw new AppException(ErrorCode.CONTRACT_NOT_FOUND);
        }
        
        return mapToResponse(contract);
    }
}
