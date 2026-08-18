package com.company.company_clean_hub_be.cccd.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CccdExtractedData {
    private String idCard;       // Số CCCD (12 chữ số)
    private String fullName;     // Họ và tên (Viết hoa)
    private String dateOfBirth;  // Ngày sinh (DD/MM/YYYY)
    private String gender;       // Nam / Nữ
    private String address;      // Nơi thường trú / Quê quán
}
