package com.company.company_clean_hub_be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private int code;

    public static <T> ApiResponse<T> success(String message, T data,int code) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .code(code)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, int errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .code(errorCode)
                .build();
    }
}

