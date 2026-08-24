package com.company.company_clean_hub_be.cccd.template;

import com.company.company_clean_hub_be.cccd.enums.DocumentSide;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CccdTemplate {

    private String templateId;              // "CCCD_FRONT_V1"
    private String documentType;            // "CCCD"
    private DocumentSide side;             // FRONT / BACK
    private String version;                 // "v1"
    private String imagePath;               // path trong resources
    private double expectedAspectRatio;     // 1.5858 (tỷ lệ chuẩn CCCD)
    private List<RegionSpec> requiredRegions;
    private boolean active;

    @Data
    @Builder
    public static class RegionSpec {
        private String name;
        // Tọa độ tương đối [0.0 - 1.0] tính theo chiều rộng/cao template
        private double x;
        private double y;
        private double width;
        private double height;
    }
}
