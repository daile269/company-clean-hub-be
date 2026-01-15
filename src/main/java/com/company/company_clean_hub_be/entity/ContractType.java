package com.company.company_clean_hub_be.entity;

public enum ContractType {
    ONE_TIME,           // Hợp đồng 1 lần (trọn gói)
    MONTHLY_FIXED,      // Hợp đồng hàng tháng cố định (chi phí cố định theo tháng)
    MONTHLY_ACTUAL      // Hợp đồng hàng tháng theo ngày thực tế (chi phí theo số ngày làm thực tế)
}
