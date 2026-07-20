package io.epiphaneia.server.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorDetail(String code, String message, List<String> details) {
    public ErrorDetail(String code, String message) {
        this(code, message, null);
    }
}
