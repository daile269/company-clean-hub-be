package com.company.company_clean_hub_be.dto.request;

import com.company.company_clean_hub_be.entity.ProfileChangeRequest.ChangeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileChangeRequestRequest {
    
    @NotNull(message = "Employee ID không được để trống")
    private Long employeeId;
    
    @NotNull(message = "Change type không được để trống")
    private ChangeType changeType;
    
    private String fieldName;
    
    private String oldValue;
    
    @NotBlank(message = "Giá trị mới không được để trống")
    private String newValue;
    
    private String reason;
}
