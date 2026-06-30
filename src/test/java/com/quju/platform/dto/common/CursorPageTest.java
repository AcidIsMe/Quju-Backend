package com.quju.platform.dto.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("游标分页单元测试")
class CursorPageTest {

    @Test
    @DisplayName("空列表应返回 hasMore=false, nextCursor=null")
    void emptyListShouldReturnNoMore() {
        CursorPage<String> page = CursorPage.of(List.of(), 20, s -> s);
        assertTrue(page.getItems().isEmpty());
        assertFalse(page.getHasMore());
        assertNull(page.getNextCursor());
    }

    @Test
    @DisplayName("少于 limit 条数据应返回 hasMore=false")
    void lessThanLimitShouldReturnNoMore() {
        List<String> items = List.of("a", "b");
        CursorPage<String> page = CursorPage.of(items, 10, s -> s);
        assertEquals(2, page.getItems().size());
        assertFalse(page.getHasMore());
    }

    @Test
    @DisplayName("等于 limit 条数据 + N+1 检测 → hasMore 由结果是否多1条决定")
    void exactLimitWithExtraShouldShowHasMore() {
        List<String> items = List.of("a", "b", "c", "d", "e", "f");
        CursorPage<String> page = CursorPage.of(items, 5, s -> s);
        assertEquals(5, page.getItems().size());
        assertTrue(page.getHasMore());
        assertEquals("e", page.getNextCursor());
    }

    @Test
    @DisplayName("N+1 策略应移除多余的那一条(hasMore=true)")
    void nPlusOneShouldTrimLast() {
        List<String> items = List.of("x", "y", "z");
        CursorPage<String> page = CursorPage.of(items, 2, s -> s);
        assertEquals(2, page.getItems().size());
        assertTrue(page.getHasMore());
    }

    @Test
    @DisplayName("刚好 limit 条且无多余数据(hasMore=false)")
    void exactFitShouldReturnNoMore() {
        List<String> items = List.of("1", "2", "3");
        CursorPage<String> page = CursorPage.of(items, 3, s -> s);
        assertEquals(3, page.getItems().size());
        assertFalse(page.getHasMore());
    }

    @Test
    @DisplayName("nextCursor 应使用 idExtractor 最后一项")
    void nextCursorShouldBeLastItemId() {
        List<Integer> items = List.of(10, 20, 30, 40);
        CursorPage<Integer> page = CursorPage.of(items, 3, i -> "id_" + i);
        assertEquals("id_30", page.getNextCursor());
    }
}
