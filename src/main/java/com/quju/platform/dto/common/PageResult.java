package com.quju.platform.dto.common;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record PageResult<T>(List<T> records, Map<String, Object> pagination) {

    public static <T> PageResult<T> of(List<T> records, int limit) {
        List<T> safeRecords = Objects.requireNonNullElse(records, List.of());
        boolean hasMore = safeRecords.size() >= limit;
        Object nextCursor = hasMore ? safeRecords.get(safeRecords.size() - 1) : null;
        return new PageResult<>(safeRecords, Map.of(
                "has_more", hasMore,
                "limit", limit,
                "next_cursor", nextCursor == null ? "" : String.valueOf(nextCursor)
        ));
    }
}
