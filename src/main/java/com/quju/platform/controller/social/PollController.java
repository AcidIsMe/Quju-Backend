package com.quju.platform.controller.social;

import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.service.PollService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/teams")
@RequiredArgsConstructor
public class PollController {
    private final PollService pollService;

    @PostMapping("/{id}/polls")
    public ApiResponse<Map<String, Object>> createPoll(@PathVariable String id, @RequestBody Map<String, Object> body) {
        String title = (String) body.get("title");
        @SuppressWarnings("unchecked")
        List<String> options = (List<String>) body.get("options");
        return ApiResponse.ok(pollService.createPoll(id, SecurityUtil.requireCurrentUserId(), title, options));
    }

    @GetMapping("/{id}/polls")
    public ApiResponse<List<Map<String, Object>>> listPolls(@PathVariable String id) {
        return ApiResponse.ok(pollService.listPolls(id));
    }

    @PostMapping("/{id}/polls/{pid}/vote")
    public ApiResponse<Void> vote(@PathVariable String id, @PathVariable String pid, @RequestBody Map<String, String> body) {
        pollService.vote(pid, body.get("option_id"), SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/polls/{pid}/close")
    public ApiResponse<Void> closePoll(@PathVariable String id, @PathVariable String pid) {
        pollService.closePoll(pid, SecurityUtil.requireCurrentUserId());
        return ApiResponse.ok();
    }
}
