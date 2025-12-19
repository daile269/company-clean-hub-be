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
import com.company.company_clean_hub_be.repository.InvoiceLineRepository;
import com.company.company_clean_hub_be.repository.AssignmentRepository;
import com.company.company_clean_hub_be.repository.AttendanceRepository;
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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class InvoiceServiceImpl implements InvoiceService {

    InvoiceRepository invoiceRepository;
    InvoiceLineRepository invoiceLineRepository;
    ContractRepository contractRepository;
    CustomerRepository customerRepository;
    AssignmentRepository assignmentRepository;
    AttendanceRepository attendanceRepository;
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

        // Lấy thông tin khách hàng (snapshot tại thời điểm xuất hóa đơn)
        Customer customer = contract.getCustomer();

        // Tính số ngày làm việc trong tháng theo hợp đồng (contractDays)
        final int contractDays = calculateContractWorkingDaysInMonth(contract, request.getInvoiceMonth(), request.getInvoiceYear());
        // actualDays = computed contractDays (we compute working days at invoice creation)
        final int actualDays = contractDays;

        // Tạo invoice (chưa có invoice lines) with computed actualWorkingDays
        Invoice invoice = Invoice.builder()
            .contract(contract)
            .customerName(customer.getName())
            .customerAddress(customer.getAddress())
            .customerTaxCode(customer.getTaxCode())
            .invoiceMonth(request.getInvoiceMonth())
            .invoiceYear(request.getInvoiceYear())
            .actualWorkingDays(actualDays)
            .subtotal(BigDecimal.ZERO)
            .vatPercentage(null)
            .vatAmount(BigDecimal.ZERO)
            .totalAmount(BigDecimal.ZERO)
            .invoiceType(contract.getContractType())
            .notes(request.getNotes())
            .status(InvoiceStatus.UNPAID)
            .createdBy(createdBy)
            .build();

        invoice = invoiceRepository.save(invoice);

        // Lấy danh sách services áp dụng cho tháng này và tính base amounts (không xét nghỉ)
        List<ServiceEntity> applicableServices = getApplicableServices(contract, request.getInvoiceMonth(), request.getInvoiceYear());

        YearMonth ym = YearMonth.of(request.getInvoiceYear(), request.getInvoiceMonth());
        LocalDate firstDayOfMonth = ym.atDay(1);
        LocalDate lastDayOfMonth = ym.atEndOfMonth();

        // Tính base amount cho từng service (theo logic cũ) và tổng giá trị hợp đồng giả định
        List<java.util.Map.Entry<ServiceEntity, BigDecimal>> serviceBases = new ArrayList<>();
        BigDecimal totalContractPrice = BigDecimal.ZERO;

        for (ServiceEntity service : applicableServices) {
            LocalDate periodStart = contract.getStartDate() != null && contract.getStartDate().isAfter(firstDayOfMonth)
                    ? contract.getStartDate() : firstDayOfMonth;
            LocalDate periodEnd = contract.getEndDate() != null && contract.getEndDate().isBefore(lastDayOfMonth)
                    ? contract.getEndDate() : lastDayOfMonth;

            LocalDate serviceStart = service.getEffectiveFrom() != null && service.getEffectiveFrom().isAfter(periodStart)
                    ? service.getEffectiveFrom() : periodStart;

            int serviceDays = 0;
            if (service.getServiceType() == ServiceType.RECURRING) {
                serviceDays = countWorkingDaysBetween(contract.getWorkingDaysPerWeek(), serviceStart, periodEnd);
            }

            BigDecimal baseAmount;
            if (service.getServiceType() == ServiceType.RECURRING) {
                baseAmount = service.getPrice().multiply(BigDecimal.valueOf(Math.max(0, serviceDays)));
            } else {
                baseAmount = service.getPrice();
            }

            serviceBases.add(new java.util.AbstractMap.SimpleEntry<>(service, baseAmount));
            totalContractPrice = totalContractPrice.add(baseAmount);
        }

        // Count number of assigned employees active for this contract in the month
        Long numEmployeesLong = assignmentRepository.countDistinctActiveEmployeesByContractBefore(contract.getId(), lastDayOfMonth);
        int numEmployees = numEmployeesLong != null ? numEmployeesLong.intValue() : 0;

        // Count attendances for this contract in the month
        Long attendancesCountLong = attendanceRepository.countAttendancesByContractAndMonthYear(contract.getId(), request.getInvoiceMonth(), request.getInvoiceYear());
        int attendancesCount = attendancesCountLong != null ? attendancesCountLong.intValue() : 0;

        if (totalContractPrice.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Contract {} totalContractPrice is zero for month {}/{}", contract.getId(), request.getInvoiceMonth(), request.getInvoiceYear());
        }

        // Separate one-time and recurring service totals
        BigDecimal oneTimeTotal = BigDecimal.ZERO;
        BigDecimal recurringTotal = BigDecimal.ZERO;
        List<java.util.Map.Entry<ServiceEntity, BigDecimal>> oneTimeBases = new ArrayList<>();
        List<java.util.Map.Entry<ServiceEntity, BigDecimal>> recurringBases = new ArrayList<>();
        for (java.util.Map.Entry<ServiceEntity, BigDecimal> e : serviceBases) {
            if (e.getKey().getServiceType() == ServiceType.ONE_TIME) {
                oneTimeBases.add(e);
                oneTimeTotal = oneTimeTotal.add(e.getValue());
            } else {
                recurringBases.add(e);
                recurringTotal = recurringTotal.add(e.getValue());
            }
        }

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;

        // If contract itself is ONE_TIME, invoice is full contract price
        if (contract.getContractType() == ContractType.ONE_TIME) {
            subtotal = totalContractPrice.setScale(2, RoundingMode.HALF_UP);

            // create invoice lines using full base amounts
            for (java.util.Map.Entry<ServiceEntity, BigDecimal> entry : serviceBases) {
                ServiceEntity service = entry.getKey();
                BigDecimal lineBase = entry.getValue().setScale(2, RoundingMode.HALF_UP);
                BigDecimal serviceVat = service.getVat() != null ? service.getVat() : BigDecimal.ZERO;
                BigDecimal vatAmount = lineBase.multiply(serviceVat).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal totalAmount = lineBase.add(vatAmount);

                InvoiceLine invoiceLine = InvoiceLine.builder()
                        .invoice(invoice)
                        .service(service)
                        .title(service.getTitle())
                        .description(service.getDescription())
                        .serviceType(service.getServiceType())
                        .unit("Dịch vụ")
                        .quantity(1)
                        .price(service.getPrice())
                        .baseAmount(lineBase)
                        .vat(serviceVat)
                        .vatAmount(vatAmount)
                        .totalAmount(totalAmount)
                        .effectiveFrom(service.getEffectiveFrom())
                        .contractDays(contractDays)
                        .actualDays(attendancesCount)
                        .build();

                invoiceLineRepository.save(invoiceLine);
                totalVat = totalVat.add(vatAmount);
            }

        } else {
            // Monthly contracts: one-time services billed fully; recurring portion billed by attendance
            if (recurringTotal.compareTo(BigDecimal.ZERO) > 0) {
                if (contractDays <= 0 || numEmployees <= 0) {
                    throw new AppException(ErrorCode.INVALID_ACTUAL_WORKING_DAYS);
                }
                BigDecimal denom = BigDecimal.valueOf((long) contractDays * numEmployees);
                BigDecimal pricePerDayPerEmployee = recurringTotal.divide(denom, 2, RoundingMode.HALF_UP);
                BigDecimal recurringSubtotal = pricePerDayPerEmployee.multiply(BigDecimal.valueOf(attendancesCount)).setScale(2, RoundingMode.HALF_UP);

                // subtotal is sum of one-time full + recurring allocated by attendance
                subtotal = oneTimeTotal.add(recurringSubtotal).setScale(2, RoundingMode.HALF_UP);

                // create one-time lines (full)
                for (java.util.Map.Entry<ServiceEntity, BigDecimal> entry : oneTimeBases) {
                    ServiceEntity service = entry.getKey();
                    BigDecimal lineBase = entry.getValue().setScale(2, RoundingMode.HALF_UP);
                    BigDecimal serviceVat = service.getVat() != null ? service.getVat() : BigDecimal.ZERO;
                    BigDecimal vatAmount = lineBase.multiply(serviceVat).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    BigDecimal totalAmount = lineBase.add(vatAmount);

                    InvoiceLine invoiceLine = InvoiceLine.builder()
                            .invoice(invoice)
                            .service(service)
                            .title(service.getTitle())
                            .description(service.getDescription())
                            .serviceType(service.getServiceType())
                            .unit("Dịch vụ")
                            .quantity(1)
                            .price(service.getPrice())
                            .baseAmount(lineBase)
                            .vat(serviceVat)
                            .vatAmount(vatAmount)
                            .totalAmount(totalAmount)
                            .effectiveFrom(service.getEffectiveFrom())
                            .contractDays(contractDays)
                            .actualDays(attendancesCount)
                            .build();

                    invoiceLineRepository.save(invoiceLine);
                    totalVat = totalVat.add(vatAmount);
                }

                // create recurring lines proportionally from recurringSubtotal
                for (java.util.Map.Entry<ServiceEntity, BigDecimal> entry : recurringBases) {
                    ServiceEntity service = entry.getKey();
                    BigDecimal serviceBase = entry.getValue();
                    BigDecimal lineBase = BigDecimal.ZERO;
                    if (recurringTotal.compareTo(BigDecimal.ZERO) > 0) {
                        BigDecimal share = serviceBase.divide(recurringTotal, 6, RoundingMode.HALF_UP);
                        lineBase = recurringSubtotal.multiply(share).setScale(2, RoundingMode.HALF_UP);
                    }

                    BigDecimal serviceVat = service.getVat() != null ? service.getVat() : BigDecimal.ZERO;
                    BigDecimal vatAmount = lineBase.multiply(serviceVat).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    BigDecimal totalAmount = lineBase.add(vatAmount);

                    InvoiceLine invoiceLine = InvoiceLine.builder()
                            .invoice(invoice)
                            .service(service)
                            .title(service.getTitle())
                            .description(service.getDescription())
                            .serviceType(service.getServiceType())
                            .unit("Dịch vụ")
                            .quantity(1)
                            .price(service.getPrice())
                            .baseAmount(lineBase)
                            .vat(serviceVat)
                            .vatAmount(vatAmount)
                            .totalAmount(totalAmount)
                            .effectiveFrom(service.getEffectiveFrom())
                            .contractDays(contractDays)
                            .actualDays(attendancesCount)
                            .build();

                    invoiceLineRepository.save(invoiceLine);
                    totalVat = totalVat.add(vatAmount);
                }

            } else {
                // No recurring services: invoice is just one-time totals
                subtotal = oneTimeTotal.setScale(2, RoundingMode.HALF_UP);
                for (java.util.Map.Entry<ServiceEntity, BigDecimal> entry : oneTimeBases) {
                    ServiceEntity service = entry.getKey();
                    BigDecimal lineBase = entry.getValue().setScale(2, RoundingMode.HALF_UP);
                    BigDecimal serviceVat = service.getVat() != null ? service.getVat() : BigDecimal.ZERO;
                    BigDecimal vatAmount = lineBase.multiply(serviceVat).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                    BigDecimal totalAmount = lineBase.add(vatAmount);

                    InvoiceLine invoiceLine = InvoiceLine.builder()
                            .invoice(invoice)
                            .service(service)
                            .title(service.getTitle())
                            .description(service.getDescription())
                            .serviceType(service.getServiceType())
                            .unit("Dịch vụ")
                            .quantity(1)
                            .price(service.getPrice())
                            .baseAmount(lineBase)
                            .vat(serviceVat)
                            .vatAmount(vatAmount)
                            .totalAmount(totalAmount)
                            .effectiveFrom(service.getEffectiveFrom())
                            .contractDays(contractDays)
                            .actualDays(attendancesCount)
                            .build();

                    invoiceLineRepository.save(invoiceLine);
                    totalVat = totalVat.add(vatAmount);
                }
            }
        }

        invoice.setSubtotal(subtotal);
        invoice.setVatAmount(totalVat);
        invoice.setTotalAmount(subtotal.add(totalVat));
        invoice = invoiceRepository.save(invoice);

        log.info("Created invoice {} for contract {} - Month {}/{} by {} with {} lines", 
            invoice.getId(), contract.getId(), request.getInvoiceMonth(), request.getInvoiceYear(), actor, applicableServices.size());

        return toInvoiceResponse(invoice);
    }

    /**
     * Tính số ngày làm việc trong tháng theo workingDaysPerWeek của hợp đồng
     */
    private int calculateContractWorkingDaysInMonth(Contract contract, int month, int year) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDate firstDayOfMonth = yearMonth.atDay(1);
        LocalDate lastDayOfMonth = yearMonth.atEndOfMonth();

        // Determine effective period for this contract within the invoice month
        LocalDate periodStart = contract.getStartDate() != null && contract.getStartDate().isAfter(firstDayOfMonth)
                ? contract.getStartDate() : firstDayOfMonth;

        LocalDate periodEnd = (contract.getEndDate() != null && contract.getEndDate().isBefore(lastDayOfMonth))
                ? contract.getEndDate() : lastDayOfMonth;

        if (periodStart.isAfter(periodEnd)) {
            log.info("Contract {} has no overlap with {}/{}", contract.getId(), month, year);
            return 0;
        }

        if (contract.getWorkingDaysPerWeek() == null || contract.getWorkingDaysPerWeek().isEmpty()) {
            // Fallback: return number of days in the period
            int days = (int) (java.time.temporal.ChronoUnit.DAYS.between(periodStart, periodEnd) + 1);
            log.info("Contract {} working days (no pattern) in {}/{}: {}", contract.getId(), month, year, days);
            return days;
        }

        int workingDays = countWorkingDaysBetween(contract.getWorkingDaysPerWeek(), periodStart, periodEnd);
        log.info("Contract {} working days in {}/{}: {} (period {} - {})", contract.getId(), month, year, workingDays, periodStart, periodEnd);
        return workingDays;
    }

    private int countWorkingDaysBetween(List<java.time.DayOfWeek> workingDaysPerWeek, LocalDate start, LocalDate end) {
        if (start == null || end == null || start.isAfter(end)) return 0;
        int count = 0;
        LocalDate cur = start;
        while (!cur.isAfter(end)) {
            if (workingDaysPerWeek.contains(cur.getDayOfWeek())) count++;
            cur = cur.plusDays(1);
        }
        return count;
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

                // Create invoice request (actual working days will be computed automatically)
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


    /**
     * Lọc services áp dụng cho tháng/năm cụ thể dựa trên effectiveFrom và serviceType
     */
    private List<ServiceEntity> getApplicableServices(Contract contract, Integer month, Integer year) {
        YearMonth invoiceMonth = YearMonth.of(year, month);
        LocalDate firstDayOfMonth = invoiceMonth.atDay(1);
        LocalDate lastDayOfMonth = invoiceMonth.atEndOfMonth();
        
        return contract.getServices().stream()
                .filter(service -> {
                    // Kiểm tra effectiveFrom
                    if (service.getEffectiveFrom() == null) {
                        log.warn("Service {} has null effectiveFrom, skipping", service.getId());
                        return false;
                    }
                    
                    // Với dịch vụ ONE_TIME: chỉ áp dụng trong tháng có effectiveFrom
                    if (service.getServiceType() == ServiceType.ONE_TIME) {
                        YearMonth serviceMonth = YearMonth.from(service.getEffectiveFrom());
                        boolean applicable = serviceMonth.equals(invoiceMonth);
                        log.info("Service {} (ONE_TIME, effectiveFrom={}): {} for invoice {}/{}",
                                service.getId(), service.getEffectiveFrom(), 
                                applicable ? "APPLICABLE" : "NOT APPLICABLE", month, year);
                        return applicable;
                    }
                    
                    // Với dịch vụ RECURRING: áp dụng từ effectiveFrom trở đi
                    boolean applicable = !service.getEffectiveFrom().isAfter(lastDayOfMonth);
                    log.info("Service {} (RECURRING, effectiveFrom={}): {} for invoice {}/{}",
                            service.getId(), service.getEffectiveFrom(), 
                            applicable ? "APPLICABLE" : "NOT APPLICABLE", month, year);
                    return applicable;
                })
                .collect(Collectors.toList());
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
        // Load invoice and delete invoice lines explicitly to ensure cleanup,
        // then delete the invoice entity. Cascade is configured, but explicit
        // delete makes intent clear.
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        if (invoice.getInvoiceLines() != null && !invoice.getInvoiceLines().isEmpty()) {
            invoiceLineRepository.deleteAll(invoice.getInvoiceLines());
        }

        invoiceRepository.delete(invoice);
        log.info("Deleted invoice {} by {}", id, actor);
    }

    private InvoiceResponse toInvoiceResponse(Invoice invoice) {
        Contract contract = invoice.getContract();
        Customer customer = contract.getCustomer();
        List<InvoiceLine> lines = invoiceLineRepository.findByInvoiceId(invoice.getId());
        List<com.company.company_clean_hub_be.dto.response.InvoiceLineResponse> lineResponses = lines.stream().map(l ->
            com.company.company_clean_hub_be.dto.response.InvoiceLineResponse.builder()
                .id(l.getId())
                .serviceId(l.getService() != null ? l.getService().getId() : null)
                .title(l.getTitle())
                .description(l.getDescription())
                .serviceType(l.getServiceType())
                .unit(l.getUnit())
                .quantity(l.getQuantity())
                .price(l.getPrice())
                .baseAmount(l.getBaseAmount())
                .vat(l.getVat())
                .vatAmount(l.getVatAmount())
                .totalAmount(l.getTotalAmount())
                .contractDays(l.getContractDays())
                .actualDays(l.getActualDays())
                .effectiveFrom(l.getEffectiveFrom())
                .createdAt(l.getCreatedAt())
                .createdAtFull(l.getCreatedAt() != null ? l.getCreatedAt().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null)
                .build()
        ).collect(java.util.stream.Collectors.toList());

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
            .invoiceLines(lineResponses)
                .invoiceType(invoice.getInvoiceType())
                .notes(invoice.getNotes())
                .status(invoice.getStatus())
                .createdAt(invoice.getCreatedAt())
                .createdAtFull(invoice.getCreatedAt() != null ? invoice.getCreatedAt().atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME) : null)
                .paidAt(invoice.getPaidAt())
                .createdByUsername(invoice.getCreatedBy() != null ? invoice.getCreatedBy().getUsername() : null)
                .build();
    }

    private void validateInvoiceDate(Contract contract, Integer invoiceMonth, Integer invoiceYear) {
        // Use actual issuance date (today) as invoice date
        java.time.LocalDate issuanceDate = java.time.LocalDate.now();

        // Ngày bắt đầu hợp đồng
        java.time.LocalDate contractStartDate = contract.getStartDate();

        // Kiểm tra: ngày phát hành hóa đơn (issuanceDate) phải >= ngày bắt đầu hợp đồng
        if (issuanceDate.isBefore(contractStartDate)) {
            log.error("Invoice issuance date {} is before contract start date {}", issuanceDate, contractStartDate);
            throw new AppException(ErrorCode.INVOICE_DATE_BEFORE_CONTRACT_START);
        }

        log.info("Invoice issuance date validation passed: {} >= contract start {}", issuanceDate, contractStartDate);
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

    @Override
    public ByteArrayOutputStream exportInvoiceToExcel(Long invoiceId) {
        String actor = getCurrentUsername() != null ? getCurrentUsername() : "anonymous";
        log.info("exportInvoiceToExcel requested by {}: invoiceId={}", actor, invoiceId);

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND));

        Contract contract = invoice.getContract();
        Customer customer = contract.getCustomer();

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Hóa đơn");

            // Styles
            CellStyle headerStyle = createHeaderStyle(workbook);
            CellStyle boldStyle = createBoldStyle(workbook);
            CellStyle centerStyle = createCenterStyle(workbook);
            CellStyle numberStyle = createNumberStyle(workbook);
            CellStyle currencyStyle = createCurrencyStyle(workbook);

            int rowNum = 0;

            // Row 0-2: Company header (optional - you can customize)
            Row row0 = sheet.createRow(rowNum++);
            Cell companyCell = row0.createCell(0);
            companyCell.setCellValue("HÓA ĐƠN GIÁ TRỊ GIA TĂNG");
            companyCell.setCellStyle(boldStyle);

            rowNum++; // Empty row

            // Customer info
            Row custRow1 = sheet.createRow(rowNum++);
            custRow1.createCell(0).setCellValue("Khách hàng:");
            custRow1.createCell(1).setCellValue(invoice.getCustomerName());

            Row custRow2 = sheet.createRow(rowNum++);
            custRow2.createCell(0).setCellValue("Địa chỉ:");
            custRow2.createCell(1).setCellValue(invoice.getCustomerAddress() != null ? invoice.getCustomerAddress() : "");

            Row custRow3 = sheet.createRow(rowNum++);
            custRow3.createCell(0).setCellValue("Mã số thuế:");
            custRow3.createCell(1).setCellValue(invoice.getCustomerTaxCode() != null ? invoice.getCustomerTaxCode() : "");

            Row custRow4 = sheet.createRow(rowNum++);
            custRow4.createCell(0).setCellValue("Số điện thoại:");
            custRow4.createCell(1).setCellValue(customer.getPhone() != null ? customer.getPhone() : "");

            Row invoiceInfoRow = sheet.createRow(rowNum++);
            invoiceInfoRow.createCell(0).setCellValue("Hóa đơn tháng:");
            invoiceInfoRow.createCell(1).setCellValue(invoice.getInvoiceMonth() + "/" + invoice.getInvoiceYear());

            rowNum++; // Empty row

            // Table header
            Row headerRow = sheet.createRow(rowNum++);
            String[] headers = {"STT", "Tên hàng hóa, dịch vụ", "Đơn vị tính", "Số lượng", "Đơn giá", 
                               "Thành tiền", "Thuế suất GTGT", "Tiền thuế GTGT"};
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Service rows
            Set<ServiceEntity> services = contract.getServices();
            int stt = 1;
            BigDecimal totalSubtotal = BigDecimal.ZERO;
            BigDecimal totalVat = BigDecimal.ZERO;

            for (ServiceEntity service : services) {
                Row serviceRow = sheet.createRow(rowNum++);
                
                // STT
                Cell sttCell = serviceRow.createCell(0);
                sttCell.setCellValue(stt++);
                sttCell.setCellStyle(centerStyle);

                // Tên dịch vụ
                serviceRow.createCell(1).setCellValue(service.getTitle());

                // Đơn vị tính
                String unit = getUnitByContractType(invoice.getInvoiceType());
                serviceRow.createCell(2).setCellValue(unit);

                // Số lượng
                BigDecimal quantity = getQuantityByContractType(invoice, contract);
                Cell qtyCell = serviceRow.createCell(3);
                qtyCell.setCellValue(quantity.doubleValue());
                qtyCell.setCellStyle(numberStyle);

                // Đơn giá
                BigDecimal unitPrice = service.getPrice();
                Cell priceCell = serviceRow.createCell(4);
                priceCell.setCellValue(unitPrice.doubleValue());
                priceCell.setCellStyle(currencyStyle);

                // Thành tiền
                BigDecimal amount = calculateServiceAmount(service, invoice, contract);
                Cell amountCell = serviceRow.createCell(5);
                amountCell.setCellValue(amount.doubleValue());
                amountCell.setCellStyle(currencyStyle);
                totalSubtotal = totalSubtotal.add(amount);

                // Thuế suất VAT
                BigDecimal vatRate = service.getVat() != null ? service.getVat() : BigDecimal.ZERO;
                Cell vatRateCell = serviceRow.createCell(6);
                vatRateCell.setCellValue(vatRate.doubleValue() + "%");
                vatRateCell.setCellStyle(centerStyle);

                // Tiền thuế VAT
                BigDecimal vatAmount = calculateServiceVat(service, invoice, contract);
                Cell vatAmountCell = serviceRow.createCell(7);
                vatAmountCell.setCellValue(vatAmount.doubleValue());
                vatAmountCell.setCellStyle(currencyStyle);
                totalVat = totalVat.add(vatAmount);
            }

            rowNum++; // Empty row

            // Total rows
            Row totalRow1 = sheet.createRow(rowNum++);
            totalRow1.createCell(4).setCellValue("Tổng tiền trước thuế:");
            Cell subtotalCell = totalRow1.createCell(5);
            subtotalCell.setCellValue(totalSubtotal.doubleValue());
            subtotalCell.setCellStyle(currencyStyle);

            Row totalRow2 = sheet.createRow(rowNum++);
            totalRow2.createCell(4).setCellValue("Tổng tiền thuế GTGT:");
            Cell totalVatCell = totalRow2.createCell(5);
            totalVatCell.setCellValue(totalVat.doubleValue());
            totalVatCell.setCellStyle(currencyStyle);

            Row totalRow3 = sheet.createRow(rowNum++);
            Cell totalLabelCell = totalRow3.createCell(4);
            totalLabelCell.setCellValue("Tổng cộng thanh toán:");
            totalLabelCell.setCellStyle(boldStyle);
            Cell totalAmountCell = totalRow3.createCell(5);
            totalAmountCell.setCellValue(invoice.getTotalAmount().doubleValue());
            totalAmountCell.setCellStyle(currencyStyle);

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            log.info("exportInvoiceToExcel completed by {}: invoiceId={}", actor, invoiceId);
            return out;

        } catch (IOException e) {
            log.error("Error exporting invoice to Excel: invoiceId={}", invoiceId, e);
            throw new AppException(ErrorCode.INVOICE_NOT_FOUND);
        }
    }

    private String getUnitByContractType(ContractType type) {
        return switch (type) {
            case MONTHLY_FIXED, MONTHLY_ACTUAL -> "Tháng";
            case ONE_TIME -> "Lần";
        };
    }

    private BigDecimal getQuantityByContractType(Invoice invoice, Contract contract) {
        if (invoice.getInvoiceType() == ContractType.MONTHLY_ACTUAL && invoice.getActualWorkingDays() != null) {
            return BigDecimal.valueOf(invoice.getActualWorkingDays());
        }
        return BigDecimal.valueOf(1); // Default to 1 for fixed or one-time
    }

    private BigDecimal calculateServiceAmount(ServiceEntity service, Invoice invoice, Contract contract) {
        BigDecimal servicePrice = service.getPrice();
        
        if (invoice.getInvoiceType() == ContractType.MONTHLY_ACTUAL && invoice.getActualWorkingDays() != null) {
            Integer contractWorkingDays = contract.getWorkingDaysPerWeek() != null && !contract.getWorkingDaysPerWeek().isEmpty()
                    ? contract.getWorkingDaysPerWeek().size() * 4
                    : 20;
            
            BigDecimal actualDays = BigDecimal.valueOf(invoice.getActualWorkingDays());
            BigDecimal contractDays = BigDecimal.valueOf(contractWorkingDays);
            return servicePrice.multiply(actualDays).divide(contractDays, 2, RoundingMode.HALF_UP);
        }
        
        return servicePrice;
    }

    private BigDecimal calculateServiceVat(ServiceEntity service, Invoice invoice, Contract contract) {
        BigDecimal amount = calculateServiceAmount(service, invoice, contract);
        BigDecimal vatRate = service.getVat() != null ? service.getVat() : BigDecimal.ZERO;
        return amount.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }

    private CellStyle createBoldStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle createCenterStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setAlignment(HorizontalAlignment.CENTER);
        return style;
    }

    private CellStyle createNumberStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0.00"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle createCurrencyStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        DataFormat format = workbook.createDataFormat();
        style.setDataFormat(format.getFormat("#,##0"));
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }
}
