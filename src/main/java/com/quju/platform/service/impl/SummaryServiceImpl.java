package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.component.ai.CvClient;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.ActivitySummaryEntity;
import com.quju.platform.entity.SummaryImageEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.ActivitySummaryMapper;
import com.quju.platform.mapper.SummaryImageMapper;
import com.quju.platform.service.NotificationService;
import com.quju.platform.service.SummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class SummaryServiceImpl implements SummaryService {

    private static final List<String> VALID_CATEGORIES = List.of("group_photo", "venue", "process", "supplies", "result");

    private final ActivityMapper activityMapper;
    private final ActivitySummaryMapper activitySummaryMapper;
    private final SummaryImageMapper summaryImageMapper;
    private final CvClient cvClient;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public Map<String, Object> create(String activityId, String userId, String content, List<Map<String, Object>> images) {
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        if (!userId.equals(activity.getCreatorId())) {
            throw new BusinessException(40300, "只有活动创建者才能创建总结");
        }
        if (images == null || images.isEmpty()) {
            throw new BusinessException(40001, "至少上传一张图片");
        }

        // 检查是否已存在总结（一个活动只能有一个）
        ActivitySummaryEntity existing = activitySummaryMapper.selectOne(
                Wrappers.<ActivitySummaryEntity>lambdaQuery()
                        .eq(ActivitySummaryEntity::getActivityId, activityId));
        if (existing != null) {
            throw new BusinessException(40900, "该活动已有总结，不能重复创建");
        }

        // 创建总结
        ActivitySummaryEntity summary = new ActivitySummaryEntity();
        summary.setActivityId(activityId);
        summary.setContent(content);
        summary.setCreatedAt(LocalDateTime.now());
        activitySummaryMapper.insert(summary);

        // 保存图片
        List<Map<String, Object>> savedImages = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            Map<String, Object> img = images.get(i);
            SummaryImageEntity imageEntity = new SummaryImageEntity();
            imageEntity.setSummaryId(summary.getId());
            imageEntity.setImageUrl((String) img.getOrDefault("image_url", ""));
            imageEntity.setCategory((String) img.getOrDefault("category", "process"));
            imageEntity.setSortOrder((Integer) img.getOrDefault("sort_order", i));
            imageEntity.setCreatedAt(LocalDateTime.now());
            summaryImageMapper.insert(imageEntity);

            Map<String, Object> saved = new HashMap<>();
            saved.put("id", imageEntity.getId());
            saved.put("image_url", imageEntity.getImageUrl());
            saved.put("category", imageEntity.getCategory());
            saved.put("sort_order", imageEntity.getSortOrder());
            savedImages.add(saved);
        }

        // 发送通知
        notificationService.notify(
                userId,
                "summary_created",
                "活动总结已创建",
                "您成功创建了活动总结",
                Map.of("activity_id", activityId, "summary_id", summary.getId())
        );

        Map<String, Object> result = new HashMap<>();
        result.put("id", summary.getId());
        result.put("activity_id", summary.getActivityId());
        result.put("content", summary.getContent());
        result.put("images", savedImages);
        result.put("created_at", summary.getCreatedAt());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> update(String activityId, String userId, String content, List<Map<String, Object>> images) {
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        if (!userId.equals(activity.getCreatorId())) {
            throw new BusinessException(40300, "只有活动创建者才能编辑总结");
        }
        if (images == null || images.isEmpty()) {
            throw new BusinessException(40001, "至少上传一张图片");
        }

        // 查找已有总结
        ActivitySummaryEntity summary = activitySummaryMapper.selectOne(
                Wrappers.<ActivitySummaryEntity>lambdaQuery()
                        .eq(ActivitySummaryEntity::getActivityId, activityId));
        if (summary == null) {
            throw new BusinessException(40401, "总结不存在，请先创建");
        }

        // 更新内容
        summary.setContent(content);
        summary.setCreatedAt(LocalDateTime.now());
        activitySummaryMapper.updateById(summary);

        // 删除旧图片
        summaryImageMapper.delete(
                Wrappers.<SummaryImageEntity>lambdaQuery()
                        .eq(SummaryImageEntity::getSummaryId, summary.getId()));

        // 插入新图片
        List<Map<String, Object>> savedImages = new ArrayList<>();
        for (int i = 0; i < images.size(); i++) {
            Map<String, Object> img = images.get(i);
            SummaryImageEntity imageEntity = new SummaryImageEntity();
            imageEntity.setSummaryId(summary.getId());
            imageEntity.setImageUrl((String) img.getOrDefault("image_url", ""));
            imageEntity.setCategory((String) img.getOrDefault("category", "process"));
            imageEntity.setSortOrder((Integer) img.getOrDefault("sort_order", i));
            imageEntity.setCreatedAt(LocalDateTime.now());
            summaryImageMapper.insert(imageEntity);

            Map<String, Object> saved = new HashMap<>();
            saved.put("id", imageEntity.getId());
            saved.put("image_url", imageEntity.getImageUrl());
            saved.put("category", imageEntity.getCategory());
            saved.put("sort_order", imageEntity.getSortOrder());
            savedImages.add(saved);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", summary.getId());
        result.put("activity_id", summary.getActivityId());
        result.put("content", summary.getContent());
        result.put("images", savedImages);
        result.put("created_at", summary.getCreatedAt());
        return result;
    }

    @Override
    public Map<String, Object> detail(String activityId) {
        ActivitySummaryEntity summary = activitySummaryMapper.selectOne(
                Wrappers.<ActivitySummaryEntity>lambdaQuery()
                        .eq(ActivitySummaryEntity::getActivityId, activityId));
        if (summary == null) {
            throw new BusinessException(40401, "总结不存在");
        }

        List<SummaryImageEntity> imageEntities = summaryImageMapper.selectList(
                Wrappers.<SummaryImageEntity>lambdaQuery()
                        .eq(SummaryImageEntity::getSummaryId, summary.getId())
                        .orderByAsc(SummaryImageEntity::getSortOrder));

        List<Map<String, Object>> images = imageEntities.stream().map(img -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", img.getId());
            m.put("image_url", img.getImageUrl());
            m.put("category", img.getCategory());
            m.put("sort_order", img.getSortOrder());
            m.put("created_at", img.getCreatedAt());
            return m;
        }).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("id", summary.getId());
        result.put("activity_id", summary.getActivityId());
        result.put("content", summary.getContent());
        result.put("images", images);
        result.put("created_at", summary.getCreatedAt());
        return result;
    }

    @Override
    public Map<String, Object> classifyImages(String activityId, String userId, List<String> imageUrls) {
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        if (!userId.equals(activity.getCreatorId())) {
            throw new BusinessException(40300, "只有活动创建者才能操作");
        }
        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new BusinessException(40001, "图片列表不能为空");
        }

        // 调用AI自动分类
        List<String> categories = cvClient.classify(imageUrls.size());

        List<Map<String, String>> results = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            Map<String, String> item = new HashMap<>();
            item.put("image_url", imageUrls.get(i));
            item.put("category", categories.get(i));
            results.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("images", results);
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> updateImageCategory(String activityId, String userId, String imageId, String category) {
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        if (!userId.equals(activity.getCreatorId())) {
            throw new BusinessException(40300, "只有活动创建者才能操作");
        }
        if (category == null || !VALID_CATEGORIES.contains(category)) {
            throw new BusinessException(40001, "无效的分类，有效值: group_photo, venue, process, supplies, result");
        }

        SummaryImageEntity imageEntity = summaryImageMapper.selectById(imageId);
        if (imageEntity == null) {
            throw new BusinessException(40401, "图片不存在");
        }

        imageEntity.setCategory(category);
        summaryImageMapper.updateById(imageEntity);

        Map<String, Object> result = new HashMap<>();
        result.put("id", imageEntity.getId());
        result.put("image_url", imageEntity.getImageUrl());
        result.put("category", imageEntity.getCategory());
        result.put("sort_order", imageEntity.getSortOrder());
        return result;
    }
}
