package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerAssignmentRequest {

    @NotNull(message = "ID quản lý không được để trống")
    private Long managerId;

    @NotNull(message = "ID khách hàng không được để trống")
    private Long customerId;
}
