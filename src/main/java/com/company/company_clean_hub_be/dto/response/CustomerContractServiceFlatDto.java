package com.company.company_clean_hub_be.dto.response;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerContractServiceFlatDto {
    private Long customerId;
    private String customerName;
    private String address;
    private String taxCode;
    private String email;

    private Long contractId;
    private String startDate;
    private String endDate;
    private BigDecimal finalPrice;
    private String contractType;
    private String paymentStatus;
    private String contractDescription;
    private Integer workDays;

    private Long serviceId;
    private String serviceName;
    private BigDecimal servicePrice;
    private BigDecimal vatPercent;
}
