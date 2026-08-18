package com.company.company_clean_hub_be.cccd.template;

import com.company.company_clean_hub_be.cccd.config.CccdValidationProperties;
import com.company.company_clean_hub_be.cccd.enums.DocumentSide;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class CccdTemplateRegistry {

    private final CccdValidationProperties props;
    private final ResourceLoader resourceLoader;

    // templateId -> CccdTemplate
    private final Map<String, CccdTemplate> registry = new HashMap<>();

    @PostConstruct
    public void init() {
        registerFrontV1();
        registerBackV1();
        log.info("[CCCD] Template registry initialized with {} templates", registry.size());
    }

    // ─── Register FRONT V1 ────────────────────────────────────────────────────
    private void registerFrontV1() {
        String path = props.getTemplate().getBasePath() + "/" + props.getTemplate().getFrontV1();
        if (!resourceExists(path)) {
            log.warn("[CCCD] Template not found, skipping: {}", path);
            return;
        }

        List<CccdTemplate.RegionSpec> regions = List.of(
                // Logo + tiêu đề CHXHCNVN (góc trên trái)
                CccdTemplate.RegionSpec.builder().name("HEADER_LOGO").x(0.01).y(0.01).width(0.18).height(0.22).build(),
                // Tiêu đề "CĂN CƯỚC CÔNG DÂN" (giữa trên)
                CccdTemplate.RegionSpec.builder().name("TITLE").x(0.20).y(0.05).width(0.60).height(0.20).build(),
                // Ảnh chân dung (bên trái)
                CccdTemplate.RegionSpec.builder().name("PORTRAIT").x(0.01).y(0.25).width(0.28).height(0.65).build(),
                // Vùng số định danh (giữa)
                CccdTemplate.RegionSpec.builder().name("ID_NUMBER").x(0.30).y(0.28).width(0.68).height(0.18).build(),
                // Vùng thông tin cá nhân (giữa phải)
                CccdTemplate.RegionSpec.builder().name("PERSONAL_INFO").x(0.30).y(0.46).width(0.68).height(0.50).build()
        );

        CccdTemplate template = CccdTemplate.builder()
                .templateId("CCCD_FRONT_V1")
                .documentType("CCCD")
                .side(DocumentSide.FRONT)
                .version("v1")
                .imagePath(path)
                .expectedAspectRatio(1.5858)
                .requiredRegions(regions)
                .active(true)
                .build();

        registry.put(template.getTemplateId(), template);
        log.info("[CCCD] Registered template: CCCD_FRONT_V1 from {}", path);
    }

    // ─── Register BACK V1 ─────────────────────────────────────────────────────
    private void registerBackV1() {
        String path = props.getTemplate().getBasePath() + "/" + props.getTemplate().getBackV1();
        if (!resourceExists(path)) {
            log.warn("[CCCD] Template not found, skipping: {}", path);
            return;
        }

        List<CccdTemplate.RegionSpec> regions = List.of(
                // Vùng đặc điểm nhận dạng + ngày cấp (trên trái)
                CccdTemplate.RegionSpec.builder().name("INFO_REGION").x(0.01).y(0.05).width(0.65).height(0.40).build(),
                // QR Code (góc trên phải - đặc trưng quan trọng nhất của mặt sau)
                CccdTemplate.RegionSpec.builder().name("QR_CODE").x(0.72).y(0.03).width(0.26).height(0.45).build(),
                // Chip NFC (góc dưới trái)
                CccdTemplate.RegionSpec.builder().name("NFC_CHIP").x(0.01).y(0.55).width(0.18).height(0.35).build(),
                // MRZ lines (dưới cùng)
                CccdTemplate.RegionSpec.builder().name("MRZ").x(0.01).y(0.72).width(0.98).height(0.28).build()
        );

        CccdTemplate template = CccdTemplate.builder()
                .templateId("CCCD_BACK_V1")
                .documentType("CCCD")
                .side(DocumentSide.BACK)
                .version("v1")
                .imagePath(path)
                .expectedAspectRatio(1.5858)
                .requiredRegions(regions)
                .active(true)
                .build();

        registry.put(template.getTemplateId(), template);
        log.info("[CCCD] Registered template: CCCD_BACK_V1 from {}", path);
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    public Optional<CccdTemplate> getTemplate(String templateId) {
        return Optional.ofNullable(registry.get(templateId));
    }

    public List<CccdTemplate> getActiveTemplatesBySide(DocumentSide side) {
        List<CccdTemplate> result = new ArrayList<>();
        for (CccdTemplate t : registry.values()) {
            if (t.isActive() && t.getSide() == side) {
                result.add(t);
            }
        }
        return result;
    }

    public List<CccdTemplate> getAllActive() {
        return registry.values().stream().filter(CccdTemplate::isActive).toList();
    }

    private boolean resourceExists(String path) {
        try {
            Resource resource = resourceLoader.getResource(path);
            return resource.exists();
        } catch (Exception e) {
            return false;
        }
    }
}
