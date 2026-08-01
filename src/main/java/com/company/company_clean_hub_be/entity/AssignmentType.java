package com.company.company_clean_hub_be.entity;

public enum AssignmentType {
    FIXED_BY_CONTRACT,  // phân công tính lương tháng
    FIXED_BY_DAY,       // phân công tính lương ngày
    TEMPORARY,          // phân công tạm thời
    FIXED_BY_COMPANY,   // phân công làm việc ở công ty
    SUPPORT             // phân công hỗ trợ (sinh attendance nhưng không tính vào hóa đơn)
}
