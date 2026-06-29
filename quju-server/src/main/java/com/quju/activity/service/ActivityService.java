package com.quju.activity.service;

import com.quju.activity.dto.CreateActivityRequest;
import com.quju.auth.service.BizException;
import com.quju.common.ErrorCode;
import com.quju.entity.Activity;
import com.quju.entity.Registration;
import com.quju.repository.ActivityRepository;
import com.quju.repository.RegistrationRepository;
import com.quju.user.entity.User;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class ActivityService {

    private final ActivityRepository activityRepository;
    private final RegistrationRepository registrationRepository;

    public ActivityService(ActivityRepository activityRepository,
                           RegistrationRepository registrationRepository) {
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
    }

    /** 创建活动 */
    @Transactional
    public Activity create(User creator, CreateActivityRequest req) {
        validateActivityRequest(req);

        String resolvedStatus = resolveStatus(req);
        Activity activity = buildActivity(creator.getId(), req, resolvedStatus);
        return activityRepository.save(activity);
    }

    /** 更新活动（仅 draft 或 rejected 可编辑） */
    @Transactional
    public Activity update(String activityId, User user, CreateActivityRequest req) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(40401, "活动不存在"));

        if (!activity.getCreatorId().equals(user.getId())) {
            throw new BizException(40305, "无权修改此活动");
        }
        if (!"draft".equals(activity.getStatus()) && !"rejected".equals(activity.getStatus())) {
            throw new BizException(40900, "仅草稿或被驳回状态可编辑");
        }

        validateActivityRequest(req);
        String resolvedStatus = resolveStatus(req);
        applyRequest(activity, req, resolvedStatus);
        return activityRepository.save(activity);
    }

    /** 获取活动详情 */
    public Activity getById(String activityId) {
        return activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(40401, "活动不存在"));
    }

    /** 删除草稿 */
    @Transactional
    public void delete(String activityId, User user) {
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(40401, "活动不存在"));

        if (!activity.getCreatorId().equals(user.getId())) {
            throw new BizException(40305, "无权删除此活动");
        }
        if (!"draft".equals(activity.getStatus())) {
            throw new BizException(40900, "仅草稿可删除");
        }
        activityRepository.delete(activity);
    }

    /** 克隆活动 */
    @Transactional
    public Activity clone(String activityId, User user) {
        Activity original = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(40401, "活动不存在"));

        Activity clone = Activity.builder()
                .creatorId(user.getId())
                .title(original.getTitle() + "（副本）")
                .description(original.getDescription())
                .tags(original.getTags())
                .activityType(original.getActivityType())
                .coverImageUrl(original.getCoverImageUrl())
                .startTime(original.getStartTime())
                .endTime(original.getEndTime())
                .registrationDeadline(original.getRegistrationDeadline())
                .maxParticipants(original.getMaxParticipants())
                .minCreditScore(original.getMinCreditScore())
                .minAge(original.getMinAge())
                .feeType(original.getFeeType())
                .feeAmount(original.getFeeAmount())
                .city(original.getCity())
                .locationName(original.getLocationName())
                .locationLat(original.getLocationLat())
                .locationLng(original.getLocationLng())
                .status("draft")
                .currentParticipants(0)
                .isTeamActivity(false)
                .checkInEnabled(false)
                .checkInLocationRequired(false)
                .clonedFromId(original.getId())
                .build();

        return activityRepository.save(clone);
    }

    /** 获取活动报名用户列表 */
    public List<Map<String, Object>> getParticipants(String activityId, int page, int limit) {
        activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(40401, "活动不存在"));

        List<Registration> registrations = registrationRepository
                .findByActivityIdAndStatus(activityId, "registered", PageRequest.of(page, limit));

        List<Map<String, Object>> result = new ArrayList<>();
        for (Registration r : registrations) {
            Map<String, Object> item = new HashMap<>();
            item.put("user_id", r.getUserId());
            item.put("status", r.getStatus());
            item.put("checked_in", r.getCheckedInAt() != null);
            item.put("created_at", r.getCreatedAt() != null ? r.getCreatedAt().toString() : null);
            result.add(item);
        }
        return result;
    }

    // ---- 私有方法 ----

    private void validateActivityRequest(CreateActivityRequest req) {
        if (req.getMaxParticipants() == null || req.getMaxParticipants() <= 0) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "人数上限必须大于0");
        }
        Instant start = parseTime(req.getStartTime());
        Instant end = parseTime(req.getEndTime());
        Instant deadline = parseTime(req.getRegistrationDeadline());

        if (!start.isBefore(end)) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "开始时间必须早于结束时间");
        }
        if (!deadline.isAfter(Instant.now())) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "报名截止时间必须晚于当前时间");
        }
        if ("free".equals(req.getFeeType()) && req.getFeeAmount() != null
                && req.getFeeAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "免费活动费用金额必须为0");
        }
    }

    private String resolveStatus(CreateActivityRequest req) {
        if ("draft".equals(req.getStatus())) {
            return "draft";
        }
        // 允许直接发布（测试/管理用，正常流程走审核）
        if ("published".equals(req.getStatus())) {
            return "published";
        }
        if (req.getMaxParticipants() != null && req.getMaxParticipants() > 50) {
            return "pending_manual_review";
        }
        return "pending_ai_review";
    }

    private Activity buildActivity(String creatorId, CreateActivityRequest req, String status) {
        return Activity.builder()
                .creatorId(creatorId)
                .title(req.getTitle())
                .description(req.getDescription())
                .tags(req.getTags() != null ? listToJson(req.getTags()) : "[]")
                .activityType(req.getActivityType())
                .coverImageUrl(req.getCoverImageUrl())
                .startTime(parseTime(req.getStartTime()))
                .endTime(parseTime(req.getEndTime()))
                .registrationDeadline(parseTime(req.getRegistrationDeadline()))
                .maxParticipants(req.getMaxParticipants())
                .minCreditScore(req.getMinCreditScore() != null ? req.getMinCreditScore() : 0)
                .minAge(req.getMinAge() != null ? req.getMinAge() : 0)
                .feeType(req.getFeeType() != null ? req.getFeeType() : "free")
                .feeAmount(req.getFeeAmount() != null ? req.getFeeAmount() : BigDecimal.ZERO)
                .city(req.getCity())
                .locationName(req.getLocationName())
                .locationLat(req.getLocationLat())
                .locationLng(req.getLocationLng())
                .status(status)
                .currentParticipants(0)
                .build();
    }

    private void applyRequest(Activity a, CreateActivityRequest req, String status) {
        a.setTitle(req.getTitle());
        a.setDescription(req.getDescription());
        a.setTags(req.getTags() != null ? listToJson(req.getTags()) : "[]");
        a.setActivityType(req.getActivityType());
        a.setCoverImageUrl(req.getCoverImageUrl());
        a.setStartTime(parseTime(req.getStartTime()));
        a.setEndTime(parseTime(req.getEndTime()));
        a.setRegistrationDeadline(parseTime(req.getRegistrationDeadline()));
        a.setMaxParticipants(req.getMaxParticipants());
        a.setMinCreditScore(req.getMinCreditScore() != null ? req.getMinCreditScore() : 0);
        a.setMinAge(req.getMinAge() != null ? req.getMinAge() : 0);
        a.setFeeType(req.getFeeType() != null ? req.getFeeType() : "free");
        a.setFeeAmount(req.getFeeAmount() != null ? req.getFeeAmount() : BigDecimal.ZERO);
        a.setCity(req.getCity());
        a.setLocationName(req.getLocationName());
        a.setLocationLat(req.getLocationLat());
        a.setLocationLng(req.getLocationLng());
        a.setStatus(status);
    }

    private Instant parseTime(String timeStr) {
        try {
            return Instant.parse(timeStr);
        } catch (Exception e) {
            throw new BizException(ErrorCode.VALIDATION_FAILED, "时间格式不正确: " + timeStr);
        }
    }

    private String listToJson(List<String> list) {
        if (list == null) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append("\"").append(list.get(i).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
        }
        sb.append("]");
        return sb.toString();
    }
}
