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
import com.quju.platform.service.RegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class RegistrationServiceImpl implements RegistrationService {

    private final ActivityMapper activityMapper;
    private final UserMapper userMapper;
    private final RegistrationMapper registrationMapper;
    private final WaitlistMapper waitlistMapper;

    @Override
    @Transactional
    public Map<String, Object> register(String activityId, String userId, Map<String, Object> formData) {
        ActivityEntity activity = activityMapper.selectById(activityId);
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
        RegistrationEntity registration = new RegistrationEntity();
        registration.setActivityId(activityId);
        registration.setUserId(userId);
        registration.setStatus("registered");
        registration.setFormData(formData);
        registrationMapper.insert(registration);
        activity.setCurrentParticipants(activity.getCurrentParticipants() + 1);
        activityMapper.updateById(activity);
        return Map.of("registration_id", registration.getId(), "status", registration.getStatus(), "current_participants", activity.getCurrentParticipants());
    }

    @Override
    @Transactional
    public void cancel(String activityId, String userId) {
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
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
        activity.setCurrentParticipants(Math.max(0, activity.getCurrentParticipants() - 1));
        activityMapper.updateById(activity);
    }

    @Override
    public WaitlistEntity joinWaitlist(String activityId, String userId) {
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
}
