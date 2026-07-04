package com.quju.platform.component.ai;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.mapper.ActivityMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AiReviewTimeoutScheduler {
    private final ActivityMapper activityMapper;

    @Scheduled(fixedRate = 60_000)
    public void autoEscalateStuckReviews() {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(5);
        List<ActivityEntity> stuck = activityMapper.selectList(
            Wrappers.<ActivityEntity>lambdaQuery()
                .eq(ActivityEntity::getStatus, "pending_ai_review")
                .lt(ActivityEntity::getCreatedAt, threshold)
        );
        for (ActivityEntity a : stuck) {
            a.setStatus("pending_manual_review");
            activityMapper.updateById(a);
            log.info("AI审核超时自动转人工: activityId={}, title={}", a.getId(), a.getTitle());
        }
    }
}
