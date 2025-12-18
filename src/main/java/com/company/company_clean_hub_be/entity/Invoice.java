package com.company.company_clean_hub_be.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Invoice {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "contract_id", nullable = false)
    Contract contract;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    List<InvoiceLine> invoiceLines = new ArrayList<>();

    // Thông tin khách hàng (lưu snapshot tại thời điểm xuất hóa đơn)
    @Column(nullable = false, length = 150)
    String customerName;

    @Column(length = 255)
    String customerAddress;

    @Column(length = 100)
    String customerTaxCode;

    // Tháng/năm xuất hóa đơn (ví dụ: 2025-12 cho tháng 12/2025)
    @Column(nullable = false)
    Integer invoiceMonth;

    @Column(nullable = false)
    Integer invoiceYear;

    // Số ngày làm thực tế (chỉ áp dụng cho MONTHLY_ACTUAL)
    Integer actualWorkingDays;

    // Tổng tiền trước VAT
    @Column(nullable = false, precision = 15, scale = 2)
    BigDecimal subtotal;

    // VAT (%)
    @Column(precision = 5, scale = 2)
    BigDecimal vatPercentage;

    // Tiền VAT
    @Column(precision = 15, scale = 2)
    BigDecimal vatAmount;

    // Tổng tiền sau VAT
    @Column(nullable = false, precision = 15, scale = 2)
    BigDecimal totalAmount;

    // Loại hóa đơn (lấy từ contract type)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    ContractType invoiceType;

    // Ghi chú
    @Column(length = 1000)
    String notes;

    // Trạng thái thanh toán
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    InvoiceStatus status = InvoiceStatus.UNPAID;

    // Ngày tạo hóa đơn
    @Column(nullable = false)
    LocalDateTime createdAt;

    // Ngày thanh toán (nếu có)
    LocalDateTime paidAt;

    // Người tạo hóa đơn
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    User createdBy;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
