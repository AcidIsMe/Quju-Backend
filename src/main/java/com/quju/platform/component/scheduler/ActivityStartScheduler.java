package com.quju.platform.component.scheduler;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.NotificationEntity;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.NotificationMapper;
import com.quju.platform.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时扫描已到开始时间的活动，向创建者发送「活动开始」系统通知
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ActivityStartScheduler {

    private final ActivityMapper activityMapper;
    private final NotificationMapper notificationMapper;
    private final NotificationService notificationService;

    /** 内存防重：已发送过开始通知的活动 ID 集合（服务重启会重置，但影响可控） */
    private final Set<String> notifiedActivityIds = ConcurrentHashMap.newKeySet();

    @Scheduled(fixedRate = 60_000)
    public void notifyActivityStart() {
        LocalDateTime now = LocalDateTime.now();
        // 扫描最近 5 分钟内开始的活动（覆盖定时任务可能的微小延迟）
        LocalDateTime windowStart = now.minusMinutes(5);

        // 查询已发布且已到开始时间的活动
        List<ActivityEntity> startedActivities = activityMapper.selectList(
                Wrappers.<ActivityEntity>lambdaQuery()
                        .eq(ActivityEntity::getStatus, "published")
                        .ge(ActivityEntity::getStartTime, windowStart)
                        .le(ActivityEntity::getStartTime, now)
        );

        if (startedActivities.isEmpty()) return;

        // 从数据库查近期 activity_start 通知，提取已通知的活动 ID（作为防重补充）
        Set<String> dbNotifiedIds = loadRecentNotifiedIds();

        for (ActivityEntity activity : startedActivities) {
            if (notifiedActivityIds.contains(activity.getId())
                    || dbNotifiedIds.contains(activity.getId())) {
                continue;
            }

            try {
                String creatorId = activity.getCreatorId();
                if (creatorId == null || creatorId.isBlank()) continue;

                notificationService.notify(
                        creatorId,
                        "activity_start",
                        "活动开始提醒",
                        "您的活动「" + activity.getTitle() + "」已开始",
                        Map.of("activity_id", activity.getId())
                );
                notifiedActivityIds.add(activity.getId());
                log.info("活动开始通知已发送: activityId={}, title={}, creatorId={}",
                        activity.getId(), activity.getTitle(), creatorId);
            } catch (Exception e) {
                log.warn("发送活动开始通知失败: activityId={}, error={}",
                        activity.getId(), e.getMessage());
            }
        }
    }

    /** 查询最近 1 小时内 type=activity_start 的通知，提取 activity_id */
    private Set<String> loadRecentNotifiedIds() {
        Set<String> ids = new HashSet<>();
        try {
            LocalDateTime since = LocalDateTime.now().minusHours(1);
            List<NotificationEntity> list = notificationMapper.selectList(
                    Wrappers.<NotificationEntity>lambdaQuery()
                            .eq(NotificationEntity::getType, "activity_start")
                            .ge(NotificationEntity::getCreatedAt, since)
            );
            for (NotificationEntity n : list) {
                Map<String, Object> meta = n.getMetadata();
                if (meta != null && meta.containsKey("activity_id")) {
                    Object v = meta.get("activity_id");
                    if (v != null) ids.add(v.toString());
                }
            }
        } catch (Exception e) {
            log.warn("加载近期 activity_start 通知失败: {}", e.getMessage());
        }
        return ids;
    }
}
