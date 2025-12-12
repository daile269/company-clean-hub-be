package com.company.company_clean_hub_be.dto.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerContractGroupDto {
    private Long customerId;
    private String customerName;
    private String address;
    private String taxCode;
    private String email;
    private List<ContractDetailDto> contracts;
}
