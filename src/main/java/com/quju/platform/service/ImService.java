package com.quju.platform.service;

import com.quju.platform.dto.im.ImMessageDto;
import com.quju.platform.entity.ImMessageEntity;

public interface ImService {
    ImMessageEntity send(ImMessageDto dto, String senderId);
    void recall(String messageId, String userId);
}
