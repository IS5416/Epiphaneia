package io.epiphaneia.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiListResponse<T>(List<T> data, long total) {

    public static <T> ApiListResponse<T> of(List<T> data) {
        return new ApiListResponse<>(data, data != null ? data.size() : 0);
    }
}
