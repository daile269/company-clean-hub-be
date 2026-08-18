package com.company.company_clean_hub_be.cccd.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;

@Getter
public enum ValidationErrorCode {

    // File errors
    IMAGE_EMPTY("Ảnh không được để trống"),
    INVALID_FILE_TYPE("Định dạng file không hợp lệ. Chỉ chấp nhận JPG/PNG/WebP"),
    FILE_TOO_LARGE("File ảnh vượt quá kích thước cho phép"),
    IMAGE_DECODE_FAILED("Không thể đọc được file ảnh"),

    // Quality errors
    IMAGE_TOO_SMALL("Độ phân giải ảnh quá thấp"),
    IMAGE_TOO_BLURRY("Ảnh bị mờ, vui lòng chụp lại"),
    IMAGE_TOO_DARK("Ảnh quá tối, vui lòng chụp ở nơi đủ sáng"),
    IMAGE_TOO_BRIGHT("Ảnh quá sáng, vui lòng tránh ánh sáng trực tiếp"),
    LOW_CONTRAST("Ảnh có độ tương phản thấp"),

    // Card detection errors
    CARD_NOT_DETECTED("Không phát hiện được thẻ CCCD trong ảnh"),
    CARD_CROPPED("CCCD bị cắt mất góc, vui lòng chụp đầy đủ cả thẻ"),
    INVALID_ASPECT_RATIO("Tỷ lệ kích thước không khớp với CCCD"),

    // Side classification errors
    UNKNOWN_DOCUMENT_SIDE("Không xác định được mặt trước hay mặt sau"),
    INVALID_FRONT_TEMPLATE("Ảnh không khớp với template mặt trước CCCD"),
    INVALID_BACK_TEMPLATE("Ảnh không khớp với template mặt sau CCCD"),

    // Completeness errors
    MISSING_FRONT("Thiếu ảnh mặt trước CCCD"),
    MISSING_BACK("Thiếu ảnh mặt sau CCCD"),

    // Document errors
    INVALID_DOCUMENT("Tài liệu không phải CCCD Việt Nam");

    private final String message;

    ValidationErrorCode(String message) {
        this.message = message;
    }

    @JsonValue
    public String getMessage() {
        return message;
    }
}
