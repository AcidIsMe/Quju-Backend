package com.quju.platform.service;

import com.quju.platform.entity.ActivityEntity;

import java.util.List;
import java.util.Map;

public interface TemplateService {
    Map<String, Object> list(String category);
    ActivityEntity useTemplate(String templateId, String userId);
}
