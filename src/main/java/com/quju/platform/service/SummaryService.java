package com.quju.platform.service;

import java.util.List;
import java.util.Map;

public interface SummaryService {
    Map<String, Object> create(String activityId, String userId, String content, List<Map<String, Object>> images);

    Map<String, Object> detail(String activityId);

    Map<String, Object> classifyImages(String activityId, String userId, List<String> imageUrls);

    Map<String, Object> updateImageCategory(String activityId, String userId, String imageId, String category);
}
