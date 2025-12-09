package com.company.company_clean_hub_be.service.impl;

import com.company.company_clean_hub_be.dto.request.InvoiceCreationRequest;
import com.company.company_clean_hub_be.dto.request.InvoiceUpdateRequest;
import com.company.company_clean_hub_be.dto.response.BulkInvoiceResponse;
import com.company.company_clean_hub_be.dto.response.InvoiceResponse;
import com.company.company_clean_hub_be.entity.*;
import com.company.company_clean_hub_be.exception.AppException;
import com.company.company_clean_hub_be.exception.ErrorCode;
import com.company.company_clean_hub_be.repository.ContractRepository;
import com.company.company_clean_hub_be.repository.CustomerRepository;
import com.company.company_clean_hub_be.repository.InvoiceRepository;
import com.company.company_clean_hub_be.repository.UserRepository;
import com.company.company_clean_hub_be.service.InvoiceService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    InvoiceRepository invoiceRepository;
    ContractRepository contractRepository;
    CustomerRepository customerRepository;
    UserRepository userRepository;

    @Override
    @Transactional
    public InvoiceResponse createInvoice(InvoiceCreationRequest request) {
        String actor = getCurrentUsername() != null ? getCurrentUsername() : "anonymous";
        log.info("createInvoice requested by {}: contractId={}, month={}, year={}", actor, request.getContractId(), request.getInvoiceMonth(), request.getInvoiceYear());

        // Kiểm tra hóa đơn đã tồn tại chưa
        if (invoiceRepository.existsByContractIdAndMonthAndYear(
                request.getContractId(), request.getInvoiceMonth(), request.getInvoiceYear())) {
            throw new AppException(ErrorCode.INVOICE_ALREADY_EXISTS);
        }

        // Lấy thông tin contract
        Contract contract = contractRepository.findById(request.getContractId())
                .orElseThrow(() -> new AppException(ErrorCode.CONTRACT_NOT_FOUND));

        // Kiểm tra ngày xuất hóa đơn phải >= ngày bắt đầu hợp đồng
        validateInvoiceDate(contract, request.getInvoiceMonth(), request.getInvoiceYear());

        // Lấy thông tin user đang đăng nhập
        String username = getCurrentUsername();
        User createdBy = userRepository.findByUsername(username != null ? username : "")
            .orElseThrow(() -> new AppException(ErrorCode.USER_IS_NOT_EXISTS));

        // Tính toán số tiền dựa trên loại hợp đồng
        BigDecimal subtotal = calculateSubtotal(contract, request);
        
        // Tính tổng VAT từ tất cả các dịch vụ trong hợp đồng
        BigDecimal totalVatAmount = calculateTotalVat(contract, subtotal, request);
        BigDecimal totalAmount = subtotal.add(totalVatAmount);

        // Lấy thông tin khách hàng (snapshot tại thời điểm xuất hóa đơn)
        Customer customer = contract.getCustomer();

        // Tạo invoice
        Invoice invoice = Invoice.builder()
                .contract(contract)
                .customerName(customer.getName())
                .customerAddress(customer.getAddress())
                .customerTaxCode(customer.getTaxCode())
                .invoiceMonth(request.getInvoiceMonth())
                .invoiceYear(request.getInvoiceYear())
                .actualWorkingDays(request.getActualWorkingDays())
                .subtotal(subtotal)
                .vatPercentage(null) // Không lưu VAT percentage vì mỗi service có VAT khác nhau
                .vatAmount(totalVatAmount)
                .totalAmount(totalAmount)
                .invoiceType(contract.getContractType())
                .notes(request.getNotes())
                .status(InvoiceStatus.UNPAID)
                .createdBy(createdBy)
                .build();

        invoice = invoiceRepository.save(invoice);
        log.info("Created invoice {} for contract {} - Month {}/{} by {}", 
            invoice.getId(), contract.getId(), request.getInvoiceMonth(), request.getInvoiceYear(), actor);

        return toInvoiceResponse(invoice);
    }

    @Override
    @Transactional
    public BulkInvoiceResponse createInvoicesForCustomer(InvoiceCreationRequest request) {
        String actor = getCurrentUsername() != null ? getCurrentUsername() : "anonymous";
        log.info("createInvoicesForCustomer requested by {}: customerId={}, month={}, year={}", actor, request.getCustomerId(), request.getInvoiceMonth(), request.getInvoiceYear());

        // Validate input
        if (request.getCustomerId() == null) {
            throw new AppException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        if (request.getInvoiceMonth() == null || request.getInvoiceYear() == null) {
            throw new AppException(ErrorCode.INVALID_ACTUAL_WORKING_DAYS);
        }

        // Kiểm tra customer tồn tại
        Customer customer = customerRepository.findById(request.getCustomerId())
                .orElseThrow(() -> new AppException(ErrorCode.CUSTOMER_NOT_FOUND));

        // Lấy tất cả hợp đồng của customer
        List<Contract> contracts = contractRepository.findByCustomerId(request.getCustomerId());
        
        if (contracts.isEmpty()) {
            log.warn("No contracts found for customer {}", request.getCustomerId());
            return BulkInvoiceResponse.builder()
                    .totalContracts(0)
                    .successfulInvoices(0)
                    .failedInvoices(0)
                    .createdInvoices(new ArrayList<>())
                    .errors(List.of("Không tìm thấy hợp đồng nào cho khách hàng này"))
                    .build();
        }

        List<InvoiceResponse> createdInvoices = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        // Tạo hóa đơn cho từng hợp đồng
        for (Contract contract : contracts) {
            try {
                // Kiểm tra hóa đơn đã tồn tại chưa
                if (invoiceRepository.existsByContractIdAndMonthAndYear(
                        contract.getId(), request.getInvoiceMonth(), request.getInvoiceYear())) {
                    errors.add(String.format("Hợp đồng #%d đã có hóa đơn tháng %d/%d", 
                            contract.getId(), request.getInvoiceMonth(), request.getInvoiceYear()));
                    failCount++;
                    continue;
                }

                // Tạo request cho từng hợp đồng
                InvoiceCreationRequest contractRequest = InvoiceCreationRequest.builder()
                        .contractId(contract.getId())
                        .invoiceMonth(request.getInvoiceMonth())
                        .invoiceYear(request.getInvoiceYear())
                        .notes(request.getNotes())
                        .build();

                // Lấy actualWorkingDays từ map nếu là MONTHLY_ACTUAL
                if (contract.getContractType() == ContractType.MONTHLY_ACTUAL) {
                    if (request.getContractActualWorkingDays() != null && 
                        request.getContractActualWorkingDays().containsKey(contract.getId())) {
                        contractRequest.setActualWorkingDays(
                                request.getContractActualWorkingDays().get(contract.getId()));
                    } else {
                        errors.add(String.format("Hợp đồng #%d (MONTHLY_ACTUAL) thiếu thông tin số ngày làm thực tế", 
                                contract.getId()));
                        failCount++;
                        continue;
                    }
                }

                InvoiceResponse invoiceResponse = createInvoice(contractRequest);
                createdInvoices.add(invoiceResponse);
                successCount++;
                
            } catch (Exception e) {
                errors.add(String.format("Hợp đồng #%d: %s", contract.getId(), e.getMessage()));
                failCount++;
                log.error("Failed to create invoice for contract {}", contract.getId(), e);
            }
        }

        log.info("Bulk invoice creation for customer {} - Total: {}, Success: {}, Failed: {}", 
            customer.getId(), contracts.size(), successCount, failCount);
        log.info("createInvoicesForCustomer completed by {}: success={}, failed={}", actor, successCount, failCount);

        return BulkInvoiceResponse.builder()
                .totalContracts(contracts.size())
                .successfulInvoices(successCount)
                .failedInvoices(failCount)
                .createdInvoices(createdInvoices)
                .errors(errors)
                .build();
    }

    private BigDecimal calculateSubtotal(Contract contract, InvoiceCreationRequest request) {
        BigDecimal totalServicePrice = contract.getServices().stream()
                .map(ServiceEntity::getPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return switch (contract.getContractType()) {
            case ONE_TIME -> {
                // Hợp đồng 1 lần: tính tổng giá dịch vụ
                log.info("ONE_TIME contract - Total service price: {}", totalServicePrice);
                yield totalServicePrice;
            }
            case MONTHLY_FIXED -> {
                // Hợp đồng cố định hàng tháng: tính theo giá cố định
                log.info("MONTHLY_FIXED contract - Fixed monthly price: {}", totalServicePrice);
                yield totalServicePrice;
            }
            case MONTHLY_ACTUAL -> {
                // Hợp đồng theo ngày thực tế: (giá dịch vụ * số ngày làm thực tế) / số ngày làm việc trong tháng
                if (request.getActualWorkingDays() == null || request.getActualWorkingDays() <= 0) {
                    throw new AppException(ErrorCode.INVALID_ACTUAL_WORKING_DAYS);
                }
                
                Integer contractWorkingDays = contract.getWorkingDaysPerWeek() != null && !contract.getWorkingDaysPerWeek().isEmpty()
                        ? contract.getWorkingDaysPerWeek().size() * 4 // Ước tính 4 tuần/tháng
                        : 20; // Mặc định 20 ngày/tháng nếu không có thông tin

                BigDecimal actualDays = BigDecimal.valueOf(request.getActualWorkingDays());
                BigDecimal contractDays = BigDecimal.valueOf(contractWorkingDays);
                BigDecimal result = totalServicePrice.multiply(actualDays).divide(contractDays, 2, RoundingMode.HALF_UP);
                
                log.info("MONTHLY_ACTUAL contract - Service price: {}, Actual days: {}, Contract days: {}, Result: {}",
                        totalServicePrice, actualDays, contractDays, result);
                yield result;
            }
        };
    }

    private BigDecimal calculateTotalVat(Contract contract, BigDecimal subtotal, InvoiceCreationRequest request) {
        // Tính tổng VAT dựa trên từng service
        BigDecimal totalVat = BigDecimal.ZERO;
        
        for (ServiceEntity service : contract.getServices()) {
            BigDecimal servicePrice = service.getPrice();
            BigDecimal serviceVat = service.getVat() != null ? service.getVat() : BigDecimal.ZERO;
            
            // Tính VAT cho service này
            BigDecimal vatAmount = servicePrice.multiply(serviceVat).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            
            // Điều chỉnh VAT theo loại hợp đồng
            if (contract.getContractType() == ContractType.MONTHLY_ACTUAL && request.getActualWorkingDays() != null) {
                Integer contractWorkingDays = contract.getWorkingDaysPerWeek() != null && !contract.getWorkingDaysPerWeek().isEmpty()
                        ? contract.getWorkingDaysPerWeek().size() * 4
                        : 20;
                
                BigDecimal actualDays = BigDecimal.valueOf(request.getActualWorkingDays());
                BigDecimal contractDays = BigDecimal.valueOf(contractWorkingDays);
                vatAmount = vatAmount.multiply(actualDays).divide(contractDays, 2, RoundingMode.HALF_UP);
            }
            
            totalVat = totalVat.add(vatAmount);
        }
        
        log.info("Total VAT calculated: {} for subtotal: {}", totalVat, subtotal);
        return totalVat;
    }

    @Override
    public InvoiceResponse getInvoice(Long id) {
        log.info("getInvoice requested: id={}", id);
        Invoice invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));
        InvoiceResponse resp = toInvoiceResponse(invoice);
        log.info("getInvoice completed: id={}, contractId={}", id, invoice.getContract() != null ? invoice.getContract().getId() : null);
        return resp;
    }

    @Override
    public List<InvoiceResponse> getInvoicesByContract(Long contractId) {
        log.info("getInvoicesByContract requested: contractId={}", contractId);
        List<InvoiceResponse> resp = invoiceRepository.findByContractId(contractId).stream()
            .map(this::toInvoiceResponse)
            .collect(Collectors.toList());
        log.info("getInvoicesByContract completed: contractId={}, count={}", contractId, resp.size());
        return resp;
    }

    @Override
    public List<InvoiceResponse> getInvoicesByCustomer(Long customerId) {
        log.info("getInvoicesByCustomer requested: customerId={}", customerId);
        List<InvoiceResponse> resp = invoiceRepository.findByCustomerId(customerId).stream()
            .map(this::toInvoiceResponse)
            .collect(Collectors.toList());
        log.info("getInvoicesByCustomer completed: customerId={}, count={}", customerId, resp.size());
        return resp;
    }

    @Override
    public List<InvoiceResponse> getInvoicesByStatus(InvoiceStatus status) {
        log.info("getInvoicesByStatus requested: status={}", status);
        List<InvoiceResponse> resp = invoiceRepository.findByStatus(status).stream()
            .map(this::toInvoiceResponse)
            .collect(Collectors.toList());
        log.info("getInvoicesByStatus completed: status={}, count={}", status, resp.size());
        return resp;
    }

    @Override
    public List<InvoiceResponse> getInvoicesByMonthAndYear(Integer month, Integer year) {
        log.info("getInvoicesByMonthAndYear requested: month={}, year={}", month, year);
        List<InvoiceResponse> resp = invoiceRepository.findByMonthAndYear(month, year).stream()
            .map(this::toInvoiceResponse)
            .collect(Collectors.toList());
        log.info("getInvoicesByMonthAndYear completed: month={}, year={}, count={}", month, year, resp.size());
        return resp;
    }

    @Override
    @Transactional
    public InvoiceResponse updateInvoice(Long id, InvoiceUpdateRequest request) {
        String actor = getCurrentUsername() != null ? getCurrentUsername() : "anonymous";
        log.info("updateInvoice requested by {}: id={}, status={}, notesPresent={}", actor, id, request.getStatus(), request.getNotes() != null);

        Invoice invoice = invoiceRepository.findById(id)
            .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if (request.getStatus() != null) {
            invoice.setStatus(request.getStatus());
            if (request.getStatus() == InvoiceStatus.PAID && invoice.getPaidAt() == null) {
                invoice.setPaidAt(LocalDateTime.now());
            }
        }

        if (request.getNotes() != null) {
            invoice.setNotes(request.getNotes());
        }

        invoice = invoiceRepository.save(invoice);
        log.info("Updated invoice {} by {}", id, actor);

        return toInvoiceResponse(invoice);
    }

    @Override
    @Transactional
    public void deleteInvoice(Long id) {
        String actor = getCurrentUsername() != null ? getCurrentUsername() : "anonymous";
        log.info("deleteInvoice requested by {}: id={}", actor, id);

        if (!invoiceRepository.existsById(id)) {
            throw new AppException(ErrorCode.INVOICE_NOT_FOUND);
        }
        invoiceRepository.deleteById(id);
        log.info("Deleted invoice {} by {}", id, actor);
    }

    private InvoiceResponse toInvoiceResponse(Invoice invoice) {
        Contract contract = invoice.getContract();
        Customer customer = contract.getCustomer();
        
        return InvoiceResponse.builder()
                .id(invoice.getId())
                .contractId(contract.getId())
                .customerId(customer.getId())
                .customerName(invoice.getCustomerName())
                .customerPhone(customer.getPhone())
                .customerAddress(invoice.getCustomerAddress())
                .customerTaxCode(invoice.getCustomerTaxCode())
                .invoiceMonth(invoice.getInvoiceMonth())
                .invoiceYear(invoice.getInvoiceYear())
                .actualWorkingDays(invoice.getActualWorkingDays())
                .subtotal(invoice.getSubtotal())
                .vatPercentage(invoice.getVatPercentage())
                .vatAmount(invoice.getVatAmount())
                .totalAmount(invoice.getTotalAmount())
                .invoiceType(invoice.getInvoiceType())
                .notes(invoice.getNotes())
                .status(invoice.getStatus())
                .createdAt(invoice.getCreatedAt())
                .paidAt(invoice.getPaidAt())
                .createdByUsername(invoice.getCreatedBy() != null ? invoice.getCreatedBy().getUsername() : null)
                .build();
    }

    private void validateInvoiceDate(Contract contract, Integer invoiceMonth, Integer invoiceYear) {
        // Tạo ngày đầu tháng của invoice (ví dụ: 2025-12-01)
        java.time.LocalDate invoiceDate = java.time.LocalDate.of(invoiceYear, invoiceMonth, 1);
        
        // Ngày bắt đầu hợp đồng
        java.time.LocalDate contractStartDate = contract.getStartDate();
        
        // Kiểm tra: ngày xuất hóa đơn phải >= ngày bắt đầu hợp đồng
        if (invoiceDate.isBefore(contractStartDate)) {
            log.error("Invoice date {}-{} is before contract start date {}", 
                    invoiceYear, invoiceMonth, contractStartDate);
            throw new AppException(ErrorCode.INVOICE_DATE_BEFORE_CONTRACT_START);
        }
        
        log.info("Invoice date validation passed: {}-{} >= contract start {}", 
                invoiceYear, invoiceMonth, contractStartDate);
    }

    private String getCurrentUsername() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) return null;
            return auth.getName();
        } catch (Exception e) {
            return null;
        }
    }
}
