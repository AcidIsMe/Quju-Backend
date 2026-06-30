package com.quju.platform.component.mq;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.WaitlistEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.mapper.WaitlistMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class WaitlistTimeoutListener {

    private final WaitlistMapper waitlistMapper;
    private final RegistrationMapper registrationMapper;
    private final NotificationService notificationService;

    @Transactional
    public void onTimeout(String waitlistId) {
        WaitlistEntity expired = waitlistMapper.selectById(waitlistId);
        if (expired == null || !"promoted".equals(expired.getStatus())) {
            return; // Already processed or not in promoted state
        }

        // Mark current entry as timed out
        expired.setStatus("timeout");
        waitlistMapper.updateById(expired);

        // Also cancel the auto-created registration
        RegistrationEntity reg = registrationMapper.selectOne(Wrappers.<RegistrationEntity>lambdaQuery()
                .eq(RegistrationEntity::getActivityId, expired.getActivityId())
                .eq(RegistrationEntity::getUserId, expired.getUserId())
                .eq(RegistrationEntity::getStatus, "registered"));
        if (reg != null) {
            reg.setStatus("cancelled");
            reg.setCancelledAt(LocalDateTime.now());
            registrationMapper.updateById(reg);
        }

        // Promote next in queue
        waitlistMapper.selectList(Wrappers.<WaitlistEntity>lambdaQuery()
                        .eq(WaitlistEntity::getActivityId, expired.getActivityId())
                        .eq(WaitlistEntity::getStatus, "waiting")
                        .orderByAsc(WaitlistEntity::getPosition))
                .stream()
                .findFirst()
                .ifPresent(next -> {
                    RegistrationEntity promotedReg = new RegistrationEntity();
                    promotedReg.setActivityId(next.getActivityId());
                    promotedReg.setUserId(next.getUserId());
                    promotedReg.setStatus("registered");
                    registrationMapper.insert(promotedReg);

                    next.setStatus("promoted");
                    next.setNotifiedAt(LocalDateTime.now());
                    waitlistMapper.updateById(next);

                    // Send timeout + re-promotion notification
                    notificationService.notify(
                            expired.getUserId(),
                            "waitlist_timeout",
                            "候补确认超时",
                            "您因未及时确认，候补资格已失效",
                            Map.of("activity_id", expired.getActivityId())
                    );
                    notificationService.notify(
                            next.getUserId(),
                            "waitlist_promoted",
                            "候补成功",
                            "您已从候补队列递补为正式报名者",
                            Map.of("activity_id", next.getActivityId())
                    );
                });
    }
}
