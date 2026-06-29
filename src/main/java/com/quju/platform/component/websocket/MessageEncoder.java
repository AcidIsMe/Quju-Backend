package com.quju.platform.component.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.quju.platform.dto.im.ImMessageDto;
import org.springframework.stereotype.Component;

@Component
public class MessageEncoder {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String encode(ImMessageDto dto) {
        try {
            return objectMapper.writeValueAsString(dto);
        } catch (Exception ex) {
            throw new IllegalArgumentException("invalid message", ex);
        }
    }
}
