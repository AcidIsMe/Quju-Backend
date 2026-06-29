package com.quju.registration.service;

import com.quju.auth.service.BizException;
import com.quju.common.ErrorCode;
import com.quju.entity.Activity;
import com.quju.entity.Registration;
import com.quju.repository.ActivityRepository;
import com.quju.repository.RegistrationRepository;
import com.quju.user.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.*;

@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final ActivityRepository activityRepository;

    public RegistrationService(RegistrationRepository registrationRepository,
                               ActivityRepository activityRepository) {
        this.registrationRepository = registrationRepository;
        this.activityRepository = activityRepository;
    }

    /** 报名活动 */
    @Transactional
    public Registration register(String activityId, User user, String formData) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(40401, "活动不存在"));

        // 1. 检查活动状态
        if (!"published".equals(activity.getStatus())) {
            throw new BizException(40401, "活动不可报名");
        }

        // 2. 检查报名截止时间
        if (activity.getRegistrationDeadline().isBefore(Instant.now())) {
            throw new BizException(40903, "报名已截止");
        }

        // 3. 检查是否已报名
        if (registrationRepository.existsByActivityIdAndUserIdAndStatus(activityId, user.getId(), "registered")) {
            throw new BizException(40902, "您已报名该活动");
        }

        // 4. 检查信誉分
        if (user.getCreditScore() < activity.getMinCreditScore()) {
            throw new BizException(40301, "信誉分不满足要求");
        }

        // 5. 检查年龄
        if (activity.getMinAge() > 0 && user.getBirthday() != null) {
            int age = Period.between(user.getBirthday(), LocalDate.now()).getYears();
            if (age < activity.getMinAge()) {
                throw new BizException(40304, "年龄不满足要求");
            }
        }

        // 6. 检查名额
        if (activity.getCurrentParticipants() >= activity.getMaxParticipants()) {
            throw new BizException(40901, "名额已满");
        }

        // 7. 事务内：插入报名记录 + current_participants + 1
        Registration reg = Registration.builder()
                .activityId(activityId)
                .userId(user.getId())
                .status("registered")
                .formData(formData)
                .build();
        reg = registrationRepository.save(reg);

        activity.setCurrentParticipants(activity.getCurrentParticipants() + 1);
        activityRepository.save(activity);

        return reg;
    }

    /** 取消报名 */
    @Transactional
    public void cancel(String activityId, User user) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(40401, "活动不存在"));

        Registration reg = registrationRepository.findByActivityIdAndUserId(activityId, user.getId())
                .orElseThrow(() -> new BizException(40401, "未找到报名记录"));

        if (!"registered".equals(reg.getStatus())) {
            throw new BizException(40900, "当前状态不可取消");
        }
        if (activity.getRegistrationDeadline().isBefore(Instant.now())) {
            throw new BizException(40903, "报名已截止，无法取消");
        }

        reg.setStatus("cancelled");
        reg.setCancelledAt(Instant.now());
        registrationRepository.save(reg);

        if (activity.getCurrentParticipants() > 0) {
            activity.setCurrentParticipants(activity.getCurrentParticipants() - 1);
            activityRepository.save(activity);
        }
    }

    /** 加入等待队列（简化实现） */
    public Map<String, Object> joinWaitlist(String activityId, User user) {
        activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(40401, "活动不存在"));
        // 简化：如果名额未满则引导正常报名
        throw new BizException(40901, "名额已满，暂不支持等待队列");
    }

    /** 退出等待队列 */
    public void leaveWaitlist(String activityId, User user) {
        throw new BizException(40401, "未在等待队列中");
    }
}
