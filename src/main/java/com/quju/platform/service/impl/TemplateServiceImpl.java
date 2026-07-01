package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.ActivityTemplateEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.ActivityTemplateMapper;
import com.quju.platform.service.TemplateService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TemplateServiceImpl implements TemplateService {

    private final ActivityTemplateMapper templateMapper;
    private final ActivityMapper activityMapper;

    @Override
    public Map<String, Object> list(String category) {
        List<ActivityTemplateEntity> templates = templateMapper.selectList(Wrappers.<ActivityTemplateEntity>lambdaQuery()
                .eq(category != null && !category.isBlank(), ActivityTemplateEntity::getCategory, category)
                .orderByAsc(ActivityTemplateEntity::getCategory));
        return Map.of(
                "categories", List.of("运动健身", "户外徒步", "桌游聚会", "学习交流", "公益活动", "城市探索"),
                "templates", templates);
    }

    @Override
    public ActivityEntity useTemplate(String templateId, String userId) {
        ActivityTemplateEntity template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(40406, "模板不存在");
        }
        ActivityEntity activity = new ActivityEntity();
        activity.setCreatorId(userId);
        activity.setTitle(template.getName());
        activity.setDescription(template.getDescription() == null ? "" : template.getDescription());
        activity.setTags(template.getTags());
        activity.setActivityType(template.getActivityType());
        activity.setMaxParticipants(template.getPresetMaxParticipants() == null ? 20 : template.getPresetMaxParticipants());
        activity.setCurrentParticipants(0);
        activity.setMinCreditScore(0);
        activity.setStatus("draft");
        activity.setStartTime(LocalDateTime.now().plusDays(7));
        activity.setEndTime(LocalDateTime.now().plusDays(7)
                .plusMinutes(template.getPresetDurationMinutes() == null ? 120 : template.getPresetDurationMinutes()));
        activity.setRegistrationDeadline(LocalDateTime.now().plusDays(6));
        activityMapper.insert(activity);
        return activity;
    }
}
