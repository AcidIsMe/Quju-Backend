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

    public String takeDown() {
        return "taken_down";
    }
}
