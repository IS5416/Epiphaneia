package io.epiphaneia.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(T data, ErrorDetail error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(null, new ErrorDetail(code, message));
    }

    public static <T> ApiResponse<T> error(String code, String message, List<String> details) {
        return new ApiResponse<>(null, new ErrorDetail(code, message, details));
    }
}
