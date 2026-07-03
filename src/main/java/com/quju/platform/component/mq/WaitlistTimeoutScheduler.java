package com.quju.platform.component.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.WaitlistEntity;
import com.quju.platform.mapper.WaitlistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitlistTimeoutScheduler {

    private final WaitlistMapper waitlistMapper;
    private final WaitlistTimeoutListener timeoutListener;

    /**
     * 每分钟执行一次，检查超时的候补递补记录
     * 条件：status = 'promoted' AND expires_at < now()
     */
    @Scheduled(fixedRate = 60_000)
    public void checkExpiredPromotions() {
        List<WaitlistEntity> expiredList = waitlistMapper.selectList(Wrappers.<WaitlistEntity>lambdaQuery()
                .eq(WaitlistEntity::getStatus, "promoted")
                .isNotNull(WaitlistEntity::getExpiresAt)
                .lt(WaitlistEntity::getExpiresAt, LocalDateTime.now()));

        for (WaitlistEntity entry : expiredList) {
            try {
                timeoutListener.onTimeout(entry.getId());
                log.info("Waitlist timeout processed: waitlistId={}, userId={}, activityId={}",
                        entry.getId(), entry.getUserId(), entry.getActivityId());
            } catch (Exception e) {
                log.error("Failed to process waitlist timeout: waitlistId={}", entry.getId(), e);
            }
        }
    }
}
