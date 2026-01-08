package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RollbackTerminationResponse {
    
    private boolean success;
    private int restoredCount;
    private String message;
    private Long assignmentId;
    private String employeeName;
}
