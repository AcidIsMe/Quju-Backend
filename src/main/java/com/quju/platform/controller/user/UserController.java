package com.quju.platform.controller.user;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class UserController {

    private final UserMapper userMapper;

    @GetMapping("/me")
    public ApiResponse<UserEntity> me(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        return ApiResponse.ok(userMapper.selectById(SecurityUtil.currentUserIdOr(userId)));
    }

    @PatchMapping("/me")
    public ApiResponse<UserEntity> updateMe(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                            @RequestBody Map<String, Object> body) {
        UserEntity user = userMapper.selectById(SecurityUtil.currentUserIdOr(userId));
        if (user != null) {
            if (body.containsKey("nickname")) {
                user.setNickname(String.valueOf(body.get("nickname")));
            }
            if (body.containsKey("bio")) {
                user.setBio(String.valueOf(body.get("bio")));
            }
            userMapper.updateById(user);
        }
        return ApiResponse.ok(user);
    }

    @GetMapping("/{id}")
    public ApiResponse<UserEntity> publicInfo(@PathVariable String id) {
        return ApiResponse.ok(userMapper.selectById(id));
    }

    @GetMapping("/check-nickname")
    public ApiResponse<Map<String, Object>> checkNickname(@RequestParam String nickname) {
        long count = userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery().eq(UserEntity::getNickname, nickname));
        return ApiResponse.ok(Map.of("available", count == 0));
    }
}
