package com.quju.platform.dto.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.function.Function;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CursorPage<T> {

    private List<T> items;
    private String nextCursor;
    private Boolean hasMore;
    private Integer limit;

    /**
     * 构建游标分页结果
     * @param items     查询结果（已多取一条）
     * @param limit     每页大小
     * @param idExtractor 从最后一条数据提取 cursor (ID 或时间戳)
     */
    public static <T> CursorPage<T> of(List<T> items, int limit, Function<T, String> idExtractor) {
        boolean hasMore = items.size() > limit;
        List<T> resultItems = hasMore ? items.subList(0, limit) : items;
        String nextCursor = resultItems.isEmpty() ? null : idExtractor.apply(resultItems.get(resultItems.size() - 1));
        return new CursorPage<>(resultItems, nextCursor, hasMore, limit);
    }
}
