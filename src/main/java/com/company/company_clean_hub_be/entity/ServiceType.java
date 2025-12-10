package com.company.company_clean_hub_be.entity;

/**
 * Loại dịch vụ trong hợp đồng
 */
public enum ServiceType {
    /**
     * Dịch vụ xuyên suốt - áp dụng từ effectiveFrom đến hết hợp đồng
     */
    RECURRING,
    
    /**
     * Dịch vụ 1 lần - chỉ tính trong tháng có effectiveFrom
     */
    ONE_TIME
}
