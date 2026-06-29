package com.quju.platform.service.impl;

import com.quju.platform.dto.im.ImMessageDto;
import com.quju.platform.entity.ImMessageEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ImMessageMapper;
import com.quju.platform.service.ImService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ImServiceImpl implements ImService {

    private final ImMessageMapper imMessageMapper;

    @Override
    public ImMessageEntity send(ImMessageDto dto, String senderId) {
        ImMessageEntity entity = new ImMessageEntity();
        entity.setEntityType(dto.getEntityType());
        entity.setEntityId(dto.getEntityId());
        entity.setSenderId(senderId);
        entity.setType(dto.getType());
        entity.setContent(dto.getContent());
        entity.setMetadata(dto.getMetadata());
        entity.setRecalled(false);
        imMessageMapper.insert(entity);
        return entity;
    }

    @Override
    public void recall(String messageId, String userId) {
        ImMessageEntity entity = imMessageMapper.selectById(messageId);
        if (entity == null) {
            throw new BusinessException(40405, "消息不存在");
        }
        if (entity.getCreatedAt() != null && Duration.between(entity.getCreatedAt(), LocalDateTime.now()).toMinutes() > 2) {
            throw new BusinessException(40920, "消息已超过可撤回时间");
        }
        entity.setRecalled(true);
        entity.setRecalledAt(LocalDateTime.now());
        imMessageMapper.updateById(entity);
    }
}
