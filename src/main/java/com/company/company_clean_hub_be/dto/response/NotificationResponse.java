package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private Long id;
    private String type;
    private String typeDescription;
    private String title;
    private String message;
    private Long refEmployeeId;
    private Long refAssignmentId;
    private Long refContractId;
    private Boolean isRead;
    private LocalDateTime createdAt;
}
