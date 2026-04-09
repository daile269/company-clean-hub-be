package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkScheduleStatsResponse {
    private Long total;
    private Long verified;
    private Long missed;
    private Long scheduled;
    private Long cancelled;
    private Double verifiedPercentage;
    private Double missedPercentage;
    private Double scheduledPercentage;
}
