package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.entity.WaitlistEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.mapper.WaitlistMapper;
import com.quju.platform.service.NotificationService;
import com.quju.platform.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Comparator;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class RegistrationServiceImpl implements RegistrationService {

    private final ActivityMapper activityMapper;
    private final UserMapper userMapper;
    private final RegistrationMapper registrationMapper;
    private final WaitlistMapper waitlistMapper;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public Map<String, Object> register(String activityId, String userId, Map<String, Object> formData) {
        // 使用行锁（SELECT ... FOR UPDATE）防止并发超卖
        ActivityEntity activity = activityMapper.selectByIdForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        if (!"published".equals(activity.getStatus())) {
            throw new BusinessException(40900, "活动未发布，不能报名");
        }
        if (activity.getRegistrationDeadline() != null && activity.getRegistrationDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException(40903, "报名已截止");
        }
        if (registrationMapper.selectCount(Wrappers.<RegistrationEntity>lambdaQuery()
                .eq(RegistrationEntity::getActivityId, activityId)
                .eq(RegistrationEntity::getUserId, userId)
                .ne(RegistrationEntity::getStatus, "cancelled")) > 0) {
            throw new BusinessException(40902, "您已报名该活动");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user != null && user.getCreditScore() < activity.getMinCreditScore()) {
            throw new BusinessException(40301, "信誉分不满足要求");
        }
        if (user != null && activity.getMinAge() != null && activity.getMinAge() > 0) {
            if (user.getBirthday() == null) {
                throw new BusinessException(40304, "该活动需要完善生日信息后才能报名");
            }
            int age = Period.between(user.getBirthday(), LocalDate.now()).getYears();
            if (age < activity.getMinAge()) {
                throw new BusinessException(40304, "年龄不满足活动要求");
            }
        }
        if (activity.getCurrentParticipants() >= activity.getMaxParticipants()) {
            throw new BusinessException(40901, "名额已满");
        }
        // 检查是否有已取消的报名记录，如有则复用（避免唯一键冲突）
        RegistrationEntity registration = registrationMapper.selectOne(Wrappers.<RegistrationEntity>lambdaQuery()
                .eq(RegistrationEntity::getActivityId, activityId)
                .eq(RegistrationEntity::getUserId, userId)
                .eq(RegistrationEntity::getStatus, "cancelled"));
        if (registration != null) {
            registration.setStatus("registered");
            registration.setFormData(formData);
            registration.setCancelledAt(null);
            registrationMapper.updateById(registration);
        } else {
            registration = new RegistrationEntity();
            registration.setActivityId(activityId);
            registration.setUserId(userId);
            registration.setStatus("registered");
            registration.setFormData(formData);
            registrationMapper.insert(registration);
        }
        activity.setCurrentParticipants(activity.getCurrentParticipants() + 1);
        activityMapper.updateById(activity);
        return Map.of("registration_id", registration.getId(), "status", registration.getStatus(), "current_participants", activity.getCurrentParticipants());
    }

    @Override
    @Transactional
    public void cancel(String activityId, String userId) {
        ActivityEntity activity = activityMapper.selectByIdForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        // 检查是否超过报名截止时间
        if (activity.getRegistrationDeadline() != null && activity.getRegistrationDeadline().isBefore(LocalDateTime.now())) {
            throw new BusinessException(40903, "已超过报名截止时间，无法取消报名");
        }
        RegistrationEntity registration = registrationMapper.selectOne(Wrappers.<RegistrationEntity>lambdaQuery()
                .eq(RegistrationEntity::getActivityId, activityId)
                .eq(RegistrationEntity::getUserId, userId)
                .eq(RegistrationEntity::getStatus, "registered"));
        if (registration == null) {
            throw new BusinessException(40402, "报名记录不存在");
        }
        registration.setStatus("cancelled");
        registration.setCancelledAt(LocalDateTime.now());
        registrationMapper.updateById(registration);

        // 递减当前参与人数
        activity.setCurrentParticipants(Math.max(0, activity.getCurrentParticipants() - 1));
        activityMapper.updateById(activity);

        // 自动递补：检查候补队列，取排位最前的用户进行递补
        waitlistMapper.selectList(Wrappers.<WaitlistEntity>lambdaQuery()
                        .eq(WaitlistEntity::getActivityId, activityId)
                        .eq(WaitlistEntity::getStatus, "waiting")
                        .orderByAsc(WaitlistEntity::getPosition))
                .stream()
                .findFirst()
                .ifPresent(waitlistEntry -> {
                    // 创建报名记录
                    RegistrationEntity promotedReg = new RegistrationEntity();
                    promotedReg.setActivityId(activityId);
                    promotedReg.setUserId(waitlistEntry.getUserId());
                    promotedReg.setStatus("registered");
                    registrationMapper.insert(promotedReg);

                    // 标记候补为已递补
                    waitlistEntry.setStatus("promoted");
                    waitlistEntry.setNotifiedAt(LocalDateTime.now());
                    waitlistMapper.updateById(waitlistEntry);

                    // 递增参与人数
                    activity.setCurrentParticipants(activity.getCurrentParticipants() + 1);
                    activityMapper.updateById(activity);

                    // 发送递补通知
                    notificationService.notify(
                            waitlistEntry.getUserId(),
                            "waitlist_promoted",
                            "候补成功",
                            "您已从候补队列递补为正式报名者，请在活动页面查看详情。",
                            Map.of("activity_id", activityId, "activity_title", activity.getTitle())
                    );
                });
    }

    @Override
    @Transactional
    public WaitlistEntity joinWaitlist(String activityId, String userId) {
        ActivityEntity activity = activityMapper.selectByIdForUpdate(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }

        // 严格校验：活动必须已满员
        if (activity.getCurrentParticipants() < activity.getMaxParticipants()) {
            throw new BusinessException(40905, "活动还有空余名额，请直接报名");
        }

        // 严格校验：用户不在已有等待队列中
        long existingWaitlistCount = waitlistMapper.selectCount(Wrappers.<WaitlistEntity>lambdaQuery()
                .eq(WaitlistEntity::getActivityId, activityId)
                .eq(WaitlistEntity::getUserId, userId)
                .eq(WaitlistEntity::getStatus, "waiting"));
        if (existingWaitlistCount > 0) {
            throw new BusinessException(40906, "您已在候补队列中");
        }

        // 用户已报名（且未取消）也不能加入候补
        long existingRegistrationCount = registrationMapper.selectCount(Wrappers.<RegistrationEntity>lambdaQuery()
                .eq(RegistrationEntity::getActivityId, activityId)
                .eq(RegistrationEntity::getUserId, userId)
                .ne(RegistrationEntity::getStatus, "cancelled"));
        if (existingRegistrationCount > 0) {
            throw new BusinessException(40902, "您已报名该活动，无需加入候补");
        }

        // 计算当前最大排位
        Integer maxPosition = waitlistMapper.selectList(Wrappers.<WaitlistEntity>lambdaQuery()
                        .eq(WaitlistEntity::getActivityId, activityId))
                .stream()
                .map(WaitlistEntity::getPosition)
                .max(Integer::compareTo)
                .orElse(0);

        WaitlistEntity waitlist = new WaitlistEntity();
        waitlist.setActivityId(activityId);
        waitlist.setUserId(userId);
        waitlist.setPosition(maxPosition + 1);
        waitlist.setStatus("waiting");
        waitlistMapper.insert(waitlist);
        return waitlist;
    }

    @Override
    public void leaveWaitlist(String activityId, String userId) {
        waitlistMapper.delete(Wrappers.<WaitlistEntity>lambdaQuery()
                .eq(WaitlistEntity::getActivityId, activityId)
                .eq(WaitlistEntity::getUserId, userId));
    }

    @Override
    public Map<String, Object> getWaitlistPosition(String activityId, String userId) {
        WaitlistEntity entry = waitlistMapper.selectOne(Wrappers.<WaitlistEntity>lambdaQuery()
                .eq(WaitlistEntity::getActivityId, activityId)
                .eq(WaitlistEntity::getUserId, userId)
                .eq(WaitlistEntity::getStatus, "waiting"));
        if (entry == null) {
            return Map.of("in_queue", false);
        }
        long waitingAhead = waitlistMapper.selectCount(Wrappers.<WaitlistEntity>lambdaQuery()
                .eq(WaitlistEntity::getActivityId, activityId)
                .eq(WaitlistEntity::getStatus, "waiting")
                .lt(WaitlistEntity::getPosition, entry.getPosition()));
        return Map.of(
                "in_queue", true,
                "position", entry.getPosition(),
                "waiting_count_ahead", (int) waitingAhead
        );
    }
}
