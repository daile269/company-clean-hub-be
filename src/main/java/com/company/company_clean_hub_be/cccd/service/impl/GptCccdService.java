package com.company.company_clean_hub_be.cccd.service.impl;

import com.company.company_clean_hub_be.cccd.config.OpenAiProperties;
import com.company.company_clean_hub_be.cccd.dto.CccdExtractedData;
import com.company.company_clean_hub_be.cccd.dto.CccdSideResult;
import com.company.company_clean_hub_be.cccd.dto.CccdValidationResponse;
import com.company.company_clean_hub_be.cccd.enums.DocumentSide;
import com.company.company_clean_hub_be.cccd.enums.ValidationErrorCode;
import com.company.company_clean_hub_be.cccd.enums.ValidationStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class GptCccdService {

    private final OpenAiProperties openAiProps;
    private final ObjectMapper objectMapper;

    /**
     * Tự động xác thực & bóc tách dữ liệu 2 mặt CCCD qua OpenAI GPT Vision
     */
    public CccdValidationResponse validateWithGpt(MultipartFile frontFile, MultipartFile backFile) {
        if (!openAiProps.isEnabled() || openAiProps.getKey() == null || openAiProps.getKey().trim().isEmpty()) {
            log.info("[GPT_CCCD] GPT Service disabled or key missing. Returning null to fallback.");
            return null;
        }

        try {
            log.info("[GPT_CCCD] Sending CCCD front/back images to GPT model: {}", openAiProps.getModel());

            boolean isResponsesEndpoint = openAiProps.getUrl().endsWith("/responses");
            List<Map<String, Object>> contentList = new ArrayList<>();

            String textType = isResponsesEndpoint ? "input_text" : "text";

            String promptText = "Bạn là hệ thống kiểm định giấy tờ Căn cước công dân Việt Nam. Hãy phân tích 2 ảnh CCCD đi kèm (Ảnh 1: Mặt trước, Ảnh 2: Mặt sau):\n"
                    +
                    "1. Kiểm tra ảnh có bị mờ/nhòe/lóa không đọc rõ chữ (nếu mờ đặt blurry=true).\n" +
                    "2. Kiểm tra xem có phải phôi Căn cước công dân Việt Nam hợp lệ hay không (nếu không phải đặt valid=false).\n"
                    +
                    "3. Nếu ảnh rõ và hợp lệ (valid=true, blurry=false), hãy bóc tách các thông tin chính xác:\n" +
                    "   - idCard: Số CCCD 12 chữ số\n" +
                    "   - fullName: Họ và tên (VIẾT HOA CÓ DẤU, ví dụ: LÊ VĂN ĐẠI)\n" +
                    "   - dateOfBirth: Ngày tháng năm sinh (định dạng DD/MM/YYYY)\n" +
                    "   - gender: Giới tính (Nam/Nữ)\n" +
                    "   - address: Quê quán hoặc Nơi thường trú\n\n" +
                    "Trả về duy nhất một chuỗi JSON thuần (không bọc trong markdown code block) chứa các khóa: valid (boolean), blurry (boolean), cardDetected (boolean), idCard (string), fullName (string), dateOfBirth (string), gender (string), address (string), errorMessage (string).";

            contentList.add(Map.of("type", textType, "text", promptText));

            // Thêm Mặt trước
            if (frontFile != null && !frontFile.isEmpty()) {
                String frontBase64 = encodeToBase64(frontFile);
                if (isResponsesEndpoint) {
                    contentList.add(Map.of("type", "input_image", "image_url", frontBase64));
                } else {
                    contentList.add(Map.of("type", "image_url", "image_url", Map.of("url", frontBase64)));
                }
            }

            // Thêm Mặt sau
            if (backFile != null && !backFile.isEmpty()) {
                String backBase64 = encodeToBase64(backFile);
                if (isResponsesEndpoint) {
                    contentList.add(Map.of("type", "input_image", "image_url", backBase64));
                } else {
                    contentList.add(Map.of("type", "image_url", "image_url", Map.of("url", backBase64)));
                }
            }

            // Build Request Body cho Responses / Chat Completions API
            Map<String, Object> messageMap = Map.of("role", "user", "content", contentList);
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", openAiProps.getModel());

            if (isResponsesEndpoint) {
                requestBody.put("input", List.of(messageMap));
                requestBody.put("store", true);
            } else {
                requestBody.put("messages", List.of(messageMap));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiProps.getKey().trim());

            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(openAiProps.getTimeoutSeconds() * 1000);
            requestFactory.setReadTimeout(openAiProps.getTimeoutSeconds() * 1000);

            RestTemplate restTemplate = new RestTemplate(requestFactory);
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            log.info("[GPT_CCCD] Calling OpenAI API endpoint: {}", openAiProps.getUrl());
            ResponseEntity<String> response = restTemplate.postForEntity(openAiProps.getUrl(), entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return parseGptResponse(response.getBody());
            } else {
                log.warn("[GPT_CCCD] Non-2xx response from OpenAI: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("[GPT_CCCD] Error calling GPT Vision API: {}", e.getMessage(), e);
        }

        return null;
    }

    private String encodeToBase64(MultipartFile file) throws IOException {
        String mimeType = file.getContentType();
        if (mimeType == null || mimeType.isEmpty()) {
            mimeType = "image/jpeg";
        }
        String base64Data = Base64.getEncoder().encodeToString(file.getBytes());
        return "data:" + mimeType + ";base64," + base64Data;
    }

    private CccdValidationResponse parseGptResponse(String jsonResponseBody) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponseBody);
            String rawText = "";

            // Trích xuất text từ response (hỗ trợ cả /v1/responses và /v1/chat/completions)
            if (root.has("output") && root.get("output").isArray() && root.get("output").size() > 0) {
                JsonNode firstOutput = root.get("output").get(0);
                if (firstOutput.has("content") && firstOutput.get("content").isArray()) {
                    for (JsonNode c : firstOutput.get("content")) {
                        if ("text".equals(c.path("type").asText()) || c.has("text")) {
                            rawText = c.path("text").asText();
                            break;
                        }
                    }
                }
            } else if (root.has("choices") && root.get("choices").isArray() && root.get("choices").size() > 0) {
                rawText = root.get("choices").get(0).path("message").path("content").asText();
            }

            if (rawText.isEmpty()) {
                log.warn("[GPT_CCCD] Empty text content from GPT response.");
                return null;
            }

            // Làm sạch mã markdown ```json ... ``` nếu có
            rawText = rawText.replaceAll("```json", "").replaceAll("```", "").trim();
            log.info("[GPT_CCCD] Parsed GPT JSON content: {}", rawText);

            JsonNode resJson = objectMapper.readTree(rawText);

            boolean valid = resJson.path("valid").asBoolean(true);
            boolean blurry = resJson.path("blurry").asBoolean(false);
            boolean cardDetected = resJson.path("cardDetected").asBoolean(true);
            String idCard = resJson.path("idCard").asText(null);
            String fullName = resJson.path("fullName").asText(null);
            String dateOfBirth = resJson.path("dateOfBirth").asText(null);
            String gender = resJson.path("gender").asText(null);
            String address = resJson.path("address").asText(null);
            String errorMessage = resJson.path("errorMessage").asText(null);

            List<ValidationErrorCode> errors = new ArrayList<>();

            if (blurry) {
                valid = false;
                errors.add(ValidationErrorCode.IMAGE_TOO_BLURRY);
            }
            if (!cardDetected || !valid) {
                valid = false;
                if (errors.isEmpty()) {
                    errors.add(ValidationErrorCode.INVALID_DOCUMENT);
                }
            }

            CccdExtractedData extractedData = null;
            if (idCard != null || fullName != null) {
                extractedData = CccdExtractedData.builder()
                        .idCard(idCard)
                        .fullName(fullName)
                        .dateOfBirth(dateOfBirth)
                        .gender(gender)
                        .address(address)
                        .build();
            }

            CccdSideResult frontSide = CccdSideResult.builder()
                    .side(DocumentSide.FRONT)
                    .valid(valid)
                    .status(valid ? ValidationStatus.VALID : ValidationStatus.INVALID)
                    .qualityScore(blurry ? 40 : 95)
                    .blurry(blurry)
                    .cardDetected(cardDetected)
                    .templateScore(valid ? 90 : 0)
                    .overallScore(valid ? 95 : 40)
                    .errors(errors)
                    .build();

            CccdSideResult backSide = CccdSideResult.builder()
                    .side(DocumentSide.BACK)
                    .valid(valid)
                    .status(valid ? ValidationStatus.VALID : ValidationStatus.INVALID)
                    .qualityScore(blurry ? 40 : 95)
                    .blurry(blurry)
                    .cardDetected(cardDetected)
                    .templateScore(valid ? 90 : 0)
                    .overallScore(valid ? 95 : 40)
                    .errors(errors)
                    .build();

            return CccdValidationResponse.builder()
                    .valid(valid)
                    .documentType("CCCD")
                    .status(valid ? ValidationStatus.VALID : ValidationStatus.INVALID)
                    .front(frontSide)
                    .back(backSide)
                    .overallScore(valid ? 95 : 40)
                    .extractedData(extractedData)
                    .errorMessage(errorMessage)
                    .errors(errors)
                    .build();

        } catch (Exception e) {
            log.error("[GPT_CCCD] Failed to parse GPT response content: {}", e.getMessage(), e);
        }

        return null;
    }
}
