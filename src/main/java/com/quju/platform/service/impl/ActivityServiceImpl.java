package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.component.statemachine.ActivityStateMachine;
import com.quju.platform.dto.activity.ActivityCreateReq;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.service.ActivityService;
import com.quju.platform.util.GeoJsonUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class ActivityServiceImpl implements ActivityService {

    private final ActivityMapper activityMapper;
    private final RegistrationMapper registrationMapper;
    private final ActivityStateMachine stateMachine;

    @Override
    public ActivityEntity create(ActivityCreateReq req, String creatorId) {
        ActivityEntity entity = new ActivityEntity();
        fill(entity, req);
        entity.setCreatorId(creatorId);
        entity.setStatus(resolveInitialStatus(req));
        entity.setCurrentParticipants(0);
        entity.setCheckInEnabled(false);
        entity.setCheckInLocationRequired(false);
        activityMapper.insert(entity);
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
    public List<RegistrationEntity> participants(String id) {
        return registrationMapper.selectList(Wrappers.<RegistrationEntity>lambdaQuery()
                .eq(RegistrationEntity::getActivityId, id)
                .ne(RegistrationEntity::getStatus, "cancelled"));
    }

    @Override
    @Transactional
    public ActivityEntity submitForReview(String id) {
        ActivityEntity entity = detail(id);
        entity.setStatus(stateMachine.submitForReview(entity.getStatus(), entity.getMaxParticipants()));
        activityMapper.updateById(entity);
        return entity;
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
