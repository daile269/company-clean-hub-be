package com.company.company_clean_hub_be.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvoiceExportExcel {
    // Thông tin hóa đơn
    Long invoiceId;
    String invoiceNumber;
    Integer invoiceMonth;
    Integer invoiceYear;
    String invoiceDate;
    
    // Thông tin khách hàng
    String customerName;
    String customerAddress;
    String customerTaxCode;
    String customerPhone;
    
    // Thông tin hợp đồng
    Long contractId;
    String contractType;
    Integer actualWorkingDays;
    
    // Tổng hợp
    BigDecimal subtotal;
    BigDecimal totalVatAmount;
    BigDecimal totalAmount;
    String status;
    
    // Danh sách dịch vụ (chi tiết từng service)
    List<ServiceLineItem> services;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ServiceLineItem {
        Integer stt; // STT (No)
        String serviceName; // Tên hàng hóa, dịch vụ (Name of goods and services)
        String unit; // Đơn vị tính (Unit) - "Tháng" hoặc "Lần"
        BigDecimal quantity; // Số lượng (Quantity)
        BigDecimal unitPrice; // Đơn giá (Unit price)
        BigDecimal amount; // Thành tiền (Amount)
        BigDecimal vatRate; // Thuế suất GTGT (VAT rate) - %
        BigDecimal vatAmount; // Tiền thuế GTGT (VAT Amount)
    }
}
