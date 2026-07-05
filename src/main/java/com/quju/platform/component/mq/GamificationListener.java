package com.quju.platform.component.mq;

import com.quju.platform.service.SquadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 积分事件监听器 —— 统一处理小队成员积分奖励。
 *
 * 积分规则：
 * - 报名小队活动：+10 分
 * - 活动签到：+5 分
 * - 发布小队动态：+2 分
 * - 动态被精选：+10 分
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GamificationListener {

    public static final int POINTS_ACTIVITY_REGISTER = 10;
    public static final int POINTS_ACTIVITY_CHECK_IN = 5;
    public static final int POINTS_MOMENT_POST = 2;
    public static final int POINTS_MOMENT_FEATURED = 10;

    private final SquadService squadService;

    @EventListener
    public void onPointEvent(PointEvent event) {
        try {
            squadService.addPoints(event.getTeamId(), event.getUserId(), event.getPoints());
            log.info("Points awarded: team={}, user={}, points={}, reason={}",
                    event.getTeamId(), event.getUserId(), event.getPoints(), event.getReason());
        } catch (Exception e) {
            log.warn("Failed to award points: team={}, user={}, reason={}",
                    event.getTeamId(), event.getUserId(), e.getMessage());
        }
    }
}
