package com.company.company_clean_hub_be.dto.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RollbackResponse {
    Boolean success;
    String message;
    AssignmentHistoryResponse historyDetail;
    Integer restoredAttendances;
    Integer removedAttendances;
}
