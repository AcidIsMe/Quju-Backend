package com.quju.platform.component.statemachine;

import com.quju.platform.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@DisplayName("活动状态机单元测试")
class ActivityStateMachineTest {

    private final ActivityStateMachine stateMachine = new ActivityStateMachine();

    @Test
    @DisplayName("draft 提交审核 → ≤50人 → pending_ai_review")
    void draftSubmitWithSmallMaxShouldGoToAiReview() {
        assertEquals("pending_ai_review", stateMachine.submitForReview("draft", 50));
    }

    @Test
    @DisplayName("draft 提交审核 → >50人 → pending_manual_review")
    void draftSubmitWithLargeMaxShouldGoToManualReview() {
        assertEquals("pending_manual_review", stateMachine.submitForReview("draft", 51));
    }

    @Test
    @DisplayName("rejected 重新提交 → ≤50人 → pending_ai_review")
    void rejectedResubmitWithSmallMaxShouldGoToAiReview() {
        assertEquals("pending_ai_review", stateMachine.submitForReview("rejected", 30));
    }

    @Test
    @DisplayName("published 状态提交审核应抛异常")
    void publishedSubmitShouldThrow() {
        assertThrows(BusinessException.class, () -> stateMachine.submitForReview("published", 10));
    }

    @Test
    @DisplayName("pending_ai_review 提交审核应抛异常")
    void pendingAiReviewSubmitShouldThrow() {
        assertThrows(BusinessException.class, () -> stateMachine.submitForReview("pending_ai_review", 10));
    }

    @Test
    @DisplayName("approve 应返回 published")
    void approveShouldReturnPublished() {
        assertEquals("published", stateMachine.approve());
    }

    @Test
    @DisplayName("reject 应返回 rejected")
    void rejectShouldReturnRejected() {
        assertEquals("rejected", stateMachine.reject());
    }

    @Test
    @DisplayName("takeDown 应返回 taken_down")
    void takeDownShouldReturnTakenDown() {
        assertEquals("taken_down", stateMachine.takeDown());
    }

    @Test
    @DisplayName("maxParticipants 为 null 时视为 ≤50 → pending_ai_review")
    void nullMaxParticipantsShouldGoToAiReview() {
        assertEquals("pending_ai_review", stateMachine.submitForReview("draft", null));
    }

    @Test
    @DisplayName("invalid status 提交审核应抛异常")
    void invalidStatusSubmitShouldThrow() {
        assertThrows(BusinessException.class, () -> stateMachine.submitForReview("cancelled", 10));
    }
}
