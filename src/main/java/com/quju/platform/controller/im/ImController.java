package com.quju.platform.controller.im;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.dto.im.ImMessageDto;
import com.quju.platform.entity.ImMessageEntity;
import com.quju.platform.service.ImService;
import com.quju.platform.util.SecurityUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/im")
@RequiredArgsConstructor
public class ImController {

    private final ImService imService;

    @PostMapping("/messages")
    public ApiResponse<ImMessageEntity> send(@Valid @RequestBody ImMessageDto dto) {
        return ApiResponse.ok(imService.send(dto, SecurityUtil.requireCurrentUserId()));
    }

    @PostMapping("/messages/{id}/recall")
    public ApiResponse<Void> recall(@PathVariable String id) {
        imService.recall(id, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }
}
