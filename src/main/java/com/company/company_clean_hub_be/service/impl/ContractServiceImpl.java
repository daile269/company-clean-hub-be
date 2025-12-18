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
                log.info("getContractsWithFilter requested: keyword='{}', page={}, pageSize={}", keyword, page,
                                pageSize);
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

                Contract contract = Contract.builder()
                                .customer(customer)
                                .services(services)
                                .startDate(request.getStartDate())
                                .endDate(request.getEndDate())
                                .workingDaysPerWeek(request.getWorkingDaysPerWeek())
                                .contractType(request.getContractType())
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

                contract.setCustomer(customer);
                contract.setServices(services);
                contract.setStartDate(request.getStartDate());
                contract.setEndDate(request.getEndDate());
                contract.setWorkingDaysPerWeek(request.getWorkingDaysPerWeek());
                contract.setContractType(request.getContractType());
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
                contract.setUpdatedAt(LocalDateTime.now());

                Contract updatedContract = contractRepository.save(contract);
                log.info("addServiceToContract completed: contractId={}", updatedContract.getId());
                return mapToResponse(updatedContract);
        }

        @Override
        public ContractResponse removeServiceFromContract(Long contractId, Long serviceId) {
                String username = org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication().getName();
                log.info("removeServiceFromContract by {}: contractId={}, serviceId={}", username, contractId,
                                serviceId);
                Contract contract = contractRepository.findById(contractId)
                                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

                ServiceEntity service = serviceEntityRepository.findById(serviceId)
                                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));

                contract.getServices().remove(service);
                contract.setUpdatedAt(LocalDateTime.now());

                Contract updatedContract = contractRepository.save(contract);
                log.info("removeServiceFromContract completed: contractId={}", updatedContract.getId());
                return mapToResponse(updatedContract);
        }

        @Override
        public ContractResponse updateServiceInContract(Long contractId,
                        com.company.company_clean_hub_be.dto.request.UpdateServiceInContractRequest request) {
                String username = org.springframework.security.core.context.SecurityContextHolder
                                .getContext().getAuthentication().getName();
                log.info("updateServiceInContract by {}: contractId={}, serviceId={}", username, contractId,
                                request.getServiceId());

                Contract contract = contractRepository.findById(contractId)
                                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

                ServiceEntity service = serviceEntityRepository.findById(request.getServiceId())
                                .orElseThrow(() -> new AppException(ErrorCode.SERVICE_NOT_FOUND));

                // Kiểm tra dịch vụ có trong hợp đồng không
                if (!contract.getServices().contains(service)) {
                        throw new AppException(ErrorCode.SERVICE_NOT_FOUND);
                }

                // Cập nhật thông tin dịch vụ
                if (request.getTitle() != null) {
                        service.setTitle(request.getTitle());
                }
                if (request.getDescription() != null) {
                        service.setDescription(request.getDescription());
                }
                if (request.getPrice() != null) {
                        service.setPrice(request.getPrice());
                }
                if (request.getVat() != null) {
                        service.setVat(request.getVat());
                }
                if (request.getEffectiveFrom() != null) {
                        service.setEffectiveFrom(request.getEffectiveFrom());
                }
                if (request.getServiceType() != null) {
                        service.setServiceType(request.getServiceType());
                }
                service.setUpdatedAt(LocalDateTime.now());
                serviceEntityRepository.save(service);
                contract.setUpdatedAt(LocalDateTime.now());

                Contract updatedContract = contractRepository.save(contract);
                log.info("updateServiceInContract completed: contractId={}, serviceId={}",
                                updatedContract.getId(), request.getServiceId());
                return mapToResponse(updatedContract);
        }

        private ContractResponse mapToResponse(Contract contract) {
                // Map services - no amount calculation here as pricing is done in invoices
                List<ServiceResponse> services = contract.getServices().stream()
                                .map(service -> ServiceResponse.builder()
                                                        .id(service.getId())
                                                        .title(service.getTitle())
                                                        .description(service.getDescription())
                                                        .price(service.getPrice())
                                                        .vat(service.getVat())
                                                        .effectiveFrom(service.getEffectiveFrom())
                                                        .serviceType(service.getServiceType())
                                                        .amount(null)
                                                        .baseAmount(null)
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
                                .paymentStatus(contract.getPaymentStatus())
                                .description(contract.getDescription())
                                .createdAt(contract.getCreatedAt())
                                .updatedAt(contract.getUpdatedAt())
                                .build();
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
