package com.company.company_clean_hub_be.dto.request;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdvanceNoteUpdateRequest {
    private BigDecimal advanceNote;
}
