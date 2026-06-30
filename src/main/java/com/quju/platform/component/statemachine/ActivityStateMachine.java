package com.quju.platform.component.statemachine;

import com.quju.platform.exception.BusinessException;
import org.springframework.stereotype.Component;

@Component
public class ActivityStateMachine {

    public String submitForReview(String currentStatus, Integer maxParticipants) {
        if (!"draft".equals(currentStatus) && !"rejected".equals(currentStatus)) {
            throw new BusinessException(40910, "当前状态不能提交审核");
        }
        if (maxParticipants != null && maxParticipants > 50) {
            return "pending_manual_review";
        }
        return "pending_ai_review";
    }

    public String approve() {
        return "published";
    }

    public String reject() {
        return "rejected";
    }

    public String requestChanges() {
        return "rejected";
    }

    public String takeDown() {
        return "taken_down";
    }

    /**
     * 检查是否为可审核状态（pending_ai_review 或 pending_manual_review）
     */
    public void validateReviewable(String currentStatus) {
        if (!"pending_ai_review".equals(currentStatus) && !"pending_manual_review".equals(currentStatus)) {
            throw new BusinessException(40912, "只有待审核状态的活动才能进行审核操作");
        }
    }

    /**
     * 检查是否为可下架状态
     */
    public void validateTakeDownable(String currentStatus) {
        if (!"published".equals(currentStatus)) {
            throw new BusinessException(40913, "只有已发布状态的活动才能下架");
        }
    }

    /**
     * 检查是否为可恢复状态
     */
    public void validateRestorable(String currentStatus) {
        if (!"taken_down".equals(currentStatus)) {
            throw new BusinessException(40914, "只有已下架状态的活动才能恢复");
        }
    }
}
