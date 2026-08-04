package com.company.company_clean_hub_be.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkLocationRequest {
    @NotBlank(message = "Tên vị trí không được để trống")
    private String name;

    @NotNull(message = "Vĩ độ không được để trống")
    private Double latitude;

    @NotNull(message = "Kinh độ không được để trống")
    private Double longitude;

    @NotNull(message = "Bán kính không được để trống")
    @Positive(message = "Bán kính phải lớn hơn 0")
    private Integer radiusMeters;

    private Boolean isActive;
}
