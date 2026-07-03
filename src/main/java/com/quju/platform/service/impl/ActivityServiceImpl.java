package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.component.ai.CmsClient;
import com.quju.platform.component.statemachine.ActivityStateMachine;
import com.quju.platform.dto.activity.ActivityCreateReq;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.entity.TeamMemberEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.mapper.TeamMapper;
import com.quju.platform.mapper.TeamMemberMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.ActivityService;
import com.quju.platform.service.NotificationService;
import com.quju.platform.util.GeoJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ActivityServiceImpl implements ActivityService {

    private final ActivityMapper activityMapper;
    private final UserMapper userMapper;
    private final RegistrationMapper registrationMapper;
    private final ActivityStateMachine stateMachine;
    private final CmsClient cmsClient;
    private final NotificationService notificationService;
    private final TeamMapper teamMapper;
    private final TeamMemberMapper teamMemberMapper;

    @Override
    public ActivityEntity create(ActivityCreateReq req, String creatorId) {
        // US33: 队内活动校验——小队存在性 + 队长/管理员权限
        if (Boolean.TRUE.equals(req.getTeamActivity()) && req.getTeamId() != null) {
            if (teamMapper.selectById(req.getTeamId()) == null) {
                throw new BusinessException(40404, "小队不存在");
            }
            TeamMemberEntity member = teamMemberMapper.selectOne(Wrappers.<TeamMemberEntity>lambdaQuery()
                    .eq(TeamMemberEntity::getTeamId, req.getTeamId())
                    .eq(TeamMemberEntity::getUserId, creatorId));
            if (member == null || (!"leader".equals(member.getRole()) && !"admin".equals(member.getRole()))) {
                throw new BusinessException(40300, "仅小队长或管理员可发布队内活动");
            }
        }
        ActivityEntity entity = new ActivityEntity();
        fill(entity, req);
        entity.setCreatorId(creatorId);
        String intent = resolveInitialStatus(req);
        entity.setStatus("draft");
        entity.setCurrentParticipants(0);
        entity.setCheckInEnabled(false);
        entity.setCheckInLocationRequired(false);
        activityMapper.insert(entity);
        // 非草稿意图：提交审核（状态机从 draft 过渡到合适的审核状态）
        if (!"draft".equals(intent)) {
            entity.setStatus(stateMachine.submitForReview("draft", entity.getMaxParticipants()));
            activityMapper.updateById(entity);
            processAiReview(entity);
        }
        return entity;
    }

    @Override
    public ActivityEntity update(String id, ActivityCreateReq req, String userId) {
        ActivityEntity entity = detail(id);
        if (!entity.getCreatorId().equals(userId)) {
            throw new BusinessException(40300, "仅发起人可编辑活动");
        }
        if (!"draft".equals(entity.getStatus()) && !"rejected".equals(entity.getStatus())) {
            throw new BusinessException(40910, "当前状态不可编辑");
        }
        fill(entity, req);
        entity.setUpdatedAt(LocalDateTime.now());
        activityMapper.updateById(entity);
        return entity;
    }

    @Override
    public ActivityEntity detail(String id) {
        ActivityEntity entity = activityMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        return entity;
    }

    @Override
    public Map<String, Object> detailWithAggregation(String id, String currentUserId) {
        ActivityEntity entity = detail(id);

        // 创建者信息
        Map<String, Object> creator = null;
        if (entity.getCreatorId() != null) {
            UserEntity creatorEntity = userMapper.selectById(entity.getCreatorId());
            if (creatorEntity != null) {
                creator = Map.of(
                        "id", creatorEntity.getId(),
                        "nickname", creatorEntity.getNickname() == null ? "" : creatorEntity.getNickname(),
                        "avatar_url", creatorEntity.getAvatarUrl() == null ? "" : creatorEntity.getAvatarUrl(),
                        "credit_score", creatorEntity.getCreditScore() == null ? 0 : creatorEntity.getCreditScore()
                );
            }
        }

        // 当前用户报名状态
        String registrationStatus = null;
        if (currentUserId != null) {
            RegistrationEntity reg = registrationMapper.selectOne(Wrappers.<RegistrationEntity>lambdaQuery()
                    .eq(RegistrationEntity::getActivityId, id)
                    .eq(RegistrationEntity::getUserId, currentUserId)
                    .orderByDesc(RegistrationEntity::getCreatedAt)
                    .last("LIMIT 1"));
            if (reg != null) {
                registrationStatus = reg.getStatus();
            }
        }

        // 显示状态
        String displayStatus = computeDisplayStatus(entity);

        Map<String, Object> result = new HashMap<>();
        result.put("id", entity.getId());
        result.put("creator_id", entity.getCreatorId());
        result.put("creator", creator);
        result.put("title", entity.getTitle());
        result.put("description", entity.getDescription());
        result.put("tags", entity.getTags());
        result.put("activity_type", entity.getActivityType());
        result.put("cover_image_url", entity.getCoverImageUrl());
        result.put("start_time", entity.getStartTime());
        result.put("end_time", entity.getEndTime());
        result.put("registration_deadline", entity.getRegistrationDeadline());
        result.put("max_participants", entity.getMaxParticipants());
        result.put("current_participants", entity.getCurrentParticipants());
        result.put("min_credit_score", entity.getMinCreditScore());
        result.put("min_age", entity.getMinAge());
        result.put("fee_type", entity.getFeeType());
        result.put("fee_amount", entity.getFeeAmount());
        result.put("city", entity.getCity());
        result.put("location_name", entity.getLocationName());
        result.put("location_lat", entity.getLocationLat());
        result.put("location_lng", entity.getLocationLng());
        result.put("status", entity.getStatus());
        result.put("display_status", displayStatus);
        result.put("registration_status", registrationStatus);
        result.put("ai_review_result", entity.getAiReviewResult());
        result.put("review_reason", entity.getReviewReason());
        result.put("is_team_activity", entity.getTeamActivity());
        result.put("team_id", entity.getTeamId());
        result.put("cloned_from_id", entity.getClonedFromId());
        result.put("check_in_enabled", entity.getCheckInEnabled());
        result.put("create_at", entity.getCreatedAt());
        result.put("updated_at", entity.getUpdatedAt());

        return result;
    }

    private String computeDisplayStatus(ActivityEntity entity) {
        String status = entity.getStatus();
        if (status == null) return "unknown";
        return switch (status) {
            case "draft" -> "draft";
            case "pending_ai_review", "pending_manual_review" -> "pending_review";
            case "rejected" -> "rejected";
            case "published" -> {
                LocalDateTime now = LocalDateTime.now();
                if (entity.getEndTime() != null && now.isAfter(entity.getEndTime())) {
                    yield "ended";
                } else if (entity.getStartTime() != null && now.isAfter(entity.getStartTime())) {
                    yield "ongoing";
                }
                yield "upcoming";
            }
            case "taken_down" -> "taken_down";
            case "cancelled" -> "cancelled";
            case "closed" -> "closed";
            default -> status;
        };
    }

    @Override
    public ActivityEntity cloneActivity(String id, String creatorId) {
        ActivityEntity source = detail(id);
        ActivityEntity target = new ActivityEntity();
        BeanUtils.copyProperties(source, target, "id", "createdAt", "updatedAt", "currentParticipants", "checkInQrCode");
        target.setCreatorId(creatorId);
        target.setStatus("draft");
        target.setCurrentParticipants(0);
        target.setClonedFromId(source.getId());
        activityMapper.insert(target);
        return target;
    }

    @Override
    public void deleteDraft(String id, String userId) {
        ActivityEntity entity = detail(id);
        if (!entity.getCreatorId().equals(userId)) {
            throw new BusinessException(40300, "仅发起人可删除活动");
        }
        if (!"draft".equals(entity.getStatus())) {
            throw new BusinessException(40911, "仅草稿活动可删除");
        }
        activityMapper.deleteById(id);
    }

    @Override
    public List<Map<String, Object>> participants(String id) {
        return registrationMapper.selectList(Wrappers.<RegistrationEntity>lambdaQuery()
                        .eq(RegistrationEntity::getActivityId, id)
                        .ne(RegistrationEntity::getStatus, "cancelled"))
                .stream()
                .map(reg -> {
                    UserEntity user = userMapper.selectById(reg.getUserId());
                    Map<String, Object> item = new java.util.HashMap<>();
                    item.put("user_id", reg.getUserId());
                    item.put("nickname", user != null ? user.getNickname() : "");
                    item.put("avatar_url", user != null ? user.getAvatarUrl() : "");
                    item.put("status", reg.getStatus());
                    item.put("checked_in", "checked_in".equals(reg.getStatus()));
                    item.put("created_at", reg.getCreatedAt() == null ? "" : reg.getCreatedAt().toString());
                    return item;
                })
                .toList();
    }

    @Override
    @Transactional
    public ActivityEntity submitForReview(String id) {
        ActivityEntity entity = detail(id);
        entity.setStatus(stateMachine.submitForReview(entity.getStatus(), entity.getMaxParticipants()));
        activityMapper.updateById(entity);
        return entity;
    }

    /**
     * AI 内容安全审核（US12）
     * - pass → 自动发布
     * - violation → 自动驳回
     * - uncertain → 转为 pending_manual_review 等待人工
     */
    @Override
    public ActivityEntity processAiReview(String id) {
        ActivityEntity entity = detail(id);
        processAiReview(entity);
        return detail(id); // 重新加载最新状态
    }

    private void processAiReview(ActivityEntity entity) {
        String currentStatus = entity.getStatus();
        // 只有 pending_ai_review 的活动才走自动 AI 审核
        // >50 人的活动直接进入人工队列（pending_manual_review），由管理员审核
        if (!"pending_ai_review".equals(currentStatus)) {
            return;
        }
        String result = cmsClient.reviewContent(entity.getTitle(), entity.getDescription(), entity.getTags());
        entity.setAiReviewResult(result);
        String newStatus;
        String notifyTitle;
        String notifyContent;
        if ("pass".equals(result)) {
            newStatus = "published";
            entity.setReviewReason("AI 内容安全审核通过");
            entity.setReviewedAt(LocalDateTime.now());
            notifyTitle = "活动审核通过";
            notifyContent = "您的活动「" + entity.getTitle() + "」已通过 AI 内容安全审核并自动发布。";
        } else if ("violation".equals(result)) {
            newStatus = "rejected";
            String reason = "活动内容包含违规信息，已被系统自动驳回";
            entity.setReviewReason(reason);
            entity.setReviewedAt(LocalDateTime.now());
            notifyTitle = "活动审核驳回";
            notifyContent = "您的活动「" + entity.getTitle() + "」未通过内容安全审核，原因：" + reason;
        } else {
            // uncertain — 保持当前状态，降级为人工审核
            // 转为 pending_manual_review 等待管理员处理
            newStatus = "pending_manual_review";
            entity.setReviewReason("AI 内容安全审核不确定，转人工审核");
            entity.setReviewedAt(LocalDateTime.now());
            notifyTitle = "活动已提交人工审核";
            notifyContent = "您的活动「" + entity.getTitle() + "」已提交，正在等待管理员审核。";
        }
        entity.setStatus(newStatus);
        activityMapper.updateById(entity);
        // 通知活动创建者
        if (entity.getCreatorId() != null) {
            notificationService.notify(entity.getCreatorId(), "activity_review",
                    notifyTitle, notifyContent, Map.of("activity_id", entity.getId()));
        }
    }

    private void fill(ActivityEntity entity, ActivityCreateReq req) {
        validateActivity(req);
        entity.setTitle(req.getTitle());
        entity.setDescription(req.getDescription());
        entity.setTags(req.getTags() == null ? List.of() : req.getTags());
        entity.setActivityType(req.getActivityType());
        entity.setCoverImageUrl(req.getCoverImageUrl());
        entity.setStartTime(req.getStartTime());
        entity.setEndTime(req.getEndTime());
        entity.setRegistrationDeadline(req.getRegistrationDeadline());
        entity.setMaxParticipants(req.getMaxParticipants());
        entity.setMinCreditScore(req.getMinCreditScore() == null ? 0 : req.getMinCreditScore());
        entity.setMinAge(req.getMinAge() == null ? 0 : req.getMinAge());
        entity.setFeeType(req.getFeeType() == null || req.getFeeType().isBlank() ? "free" : req.getFeeType());
        entity.setFeeAmount(req.getFeeAmount() == null ? BigDecimal.ZERO : req.getFeeAmount());
        entity.setCity(req.getCity());
        entity.setLocationName(req.getLocationName());
        entity.setLocationLat(req.getLocationLat() == null ? GeoJsonUtil.latFromPoint(req.getGeojson()) : req.getLocationLat());
        entity.setLocationLng(req.getLocationLng() == null ? GeoJsonUtil.lngFromPoint(req.getGeojson()) : req.getLocationLng());
        entity.setTeamActivity(Boolean.TRUE.equals(req.getTeamActivity()));
        entity.setTeamId(req.getTeamId());
    }

    private String resolveInitialStatus(ActivityCreateReq req) {
        if (req.getStatus() == null || "draft".equals(req.getStatus())) {
            return "draft";
        }
        return req.getMaxParticipants() != null && req.getMaxParticipants() > 50
                ? "pending_manual_review"
                : "pending_ai_review";
    }

    private void validateActivity(ActivityCreateReq req) {
        if (req.getStartTime() != null && req.getEndTime() != null && !req.getStartTime().isBefore(req.getEndTime())) {
            throw new BusinessException(40011, "start_time must be before end_time");
        }
        String feeType = req.getFeeType() == null || req.getFeeType().isBlank() ? "free" : req.getFeeType();
        BigDecimal feeAmount = req.getFeeAmount() == null ? BigDecimal.ZERO : req.getFeeAmount();
        if (!"free".equals(feeType) && !"paid".equals(feeType)) {
            throw new BusinessException(40012, "fee_type must be free or paid");
        }
        if (feeAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(40013, "fee_amount must be greater than or equal to 0");
        }
        if ("free".equals(feeType) && feeAmount.compareTo(BigDecimal.ZERO) != 0) {
            throw new BusinessException(40014, "fee_amount must be 0 when fee_type is free");
        }
    }
}
