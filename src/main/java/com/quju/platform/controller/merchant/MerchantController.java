package com.quju.platform.controller.merchant;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.dto.common.ApiResponse;
import com.quju.platform.entity.MerchantProfileEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.MerchantProfileMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.MerchantService;
import com.quju.platform.util.SecurityUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/merchants")
@RequiredArgsConstructor
@SuppressWarnings("null")
public class MerchantController {

    private final MerchantProfileMapper merchantProfileMapper;
    private final UserMapper userMapper;
    private final MerchantService merchantService;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String q,
                                                        @RequestParam(required = false) String domain,
                                                        @RequestParam(defaultValue = "1") Integer page,
                                                        @RequestParam(defaultValue = "10") Integer size) {
        Map<String, Object> result = merchantService.listApproved(q, domain, page, size);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> data = (List<Map<String, Object>>) result.get("data");
        return ApiResponse.page(data, result.get("pagination"));
    }

    @GetMapping("/me")
    public ApiResponse<MerchantProfileEntity> myProfile() {
        String userId = SecurityUtil.requireCurrentUserId();
        MerchantProfileEntity profile = merchantProfileMapper.selectOne(
                Wrappers.<MerchantProfileEntity>lambdaQuery()
                        .eq(MerchantProfileEntity::getUserId, userId));
        if (profile == null) {
            throw new BusinessException(40403, "商家资料不存在");
        }
        return ApiResponse.ok(profile);
    }

    @PatchMapping("/me")
    public ApiResponse<MerchantProfileEntity> updateProfile(@RequestBody Map<String, Object> body) {
        String userId = SecurityUtil.requireCurrentUserId();
        MerchantProfileEntity profile = merchantProfileMapper.selectOne(
                Wrappers.<MerchantProfileEntity>lambdaQuery()
                        .eq(MerchantProfileEntity::getUserId, userId));
        if (profile == null) {
            throw new BusinessException(40403, "商家资料不存在");
        }

        if (body.containsKey("merchant_name")) {
            String name = String.valueOf(body.get("merchant_name")).trim();
            if (name.isEmpty()) {
                throw new BusinessException(40002, "商家名称不能为空");
            }
            profile.setMerchantName(name);
        }
        if (body.containsKey("merchant_nickname")) {
            String nickname = String.valueOf(body.get("merchant_nickname")).trim();
            if (nickname.isEmpty()) {
                throw new BusinessException(40002, "商家昵称不能为空");
            }
            // 检查昵称唯一性（排除自己）
            MerchantProfileEntity existing = merchantProfileMapper.selectOne(
                    Wrappers.<MerchantProfileEntity>lambdaQuery()
                            .eq(MerchantProfileEntity::getMerchantNickname, nickname)
                            .ne(MerchantProfileEntity::getId, profile.getId()));
            if (existing != null) {
                throw new BusinessException(40003, "该商家昵称已被占用");
            }
            profile.setMerchantNickname(nickname);

            // 同步更新 users 表的 nickname
            UserEntity user = userMapper.selectById(userId);
            if (user != null) {
                user.setNickname(nickname);
                userMapper.updateById(user);
            }
        }
        if (body.containsKey("activity_domains")) {
            Object val = body.get("activity_domains");
            if (val instanceof List<?> domainList) {
                List<String> safeDomains = domainList.stream()
                        .filter(d -> d instanceof String && !((String) d).isBlank())
                        .map(d -> (String) d)
                        .toList();
                profile.setActivityDomains(safeDomains);
            }
        }

        merchantProfileMapper.updateById(profile);
        return ApiResponse.ok(profile);
    }
}
