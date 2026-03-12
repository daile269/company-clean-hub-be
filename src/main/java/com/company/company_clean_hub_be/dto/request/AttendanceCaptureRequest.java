package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceCaptureRequest {
    @NotNull(message = "Attendance ID is required")
    private Long attendanceId;

    @NotBlank(message = "Image Data is required")
    private String imageData; // Base64 string from frontend

    private Double latitude;
    private Double longitude;
    private String address;
}
