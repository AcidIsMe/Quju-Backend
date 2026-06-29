package com.quju.platform.dto.common;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class PageQuery {

    private String cursor;

    @Min(1)
    @Max(100)
    private Integer limit = 20;

    public int normalizedLimit() {
        if (limit == null) {
            return 20;
        }
        return Math.max(1, Math.min(limit, 100));
    }
}
