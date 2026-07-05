package com.quju.platform.service;

import java.util.List;
import java.util.Map;

public interface TeamMomentService {
    Map<String, Object> create(String teamId, String userId, String content, String imageUrl);
    List<Map<String, Object>> list(String teamId, String cursor, int limit);
    void delete(String momentId, String userId);
    void feature(String momentId, String operatorUserId);
    void unfeature(String momentId, String operatorUserId);
}
