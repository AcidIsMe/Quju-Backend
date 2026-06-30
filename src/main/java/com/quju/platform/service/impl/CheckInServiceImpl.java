package com.quju.platform.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.quju.platform.component.gis.MapSdkService;
import com.quju.platform.dto.registration.CheckInReq;
import com.quju.platform.entity.ActivityEntity;
import com.quju.platform.entity.RegistrationEntity;
import com.quju.platform.entity.UserEntity;
import com.quju.platform.exception.BusinessException;
import com.quju.platform.mapper.ActivityMapper;
import com.quju.platform.mapper.RegistrationMapper;
import com.quju.platform.mapper.UserMapper;
import com.quju.platform.service.CheckInService;
import com.quju.platform.util.QrCodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class CheckInServiceImpl implements CheckInService {

    private final ActivityMapper activityMapper;
    private final RegistrationMapper registrationMapper;
    private final UserMapper userMapper;
    private final QrCodeGenerator qrCodeGenerator;
    private final MapSdkService mapSdkService;

    @Override
    @Transactional
    public void checkIn(String activityId, String userId, CheckInReq req) {
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        if (activity.getCheckInQrCode() != null && !activity.getCheckInQrCode().equals(req.getQrData())) {
            throw new BusinessException(40304, "签到码无效");
        }
        if (!Boolean.TRUE.equals(activity.getCheckInEnabled())) {
            throw new BusinessException(40307, "签到功能未启用");
        }
        if (Boolean.TRUE.equals(activity.getCheckInLocationRequired())) {
            double distance = mapSdkService.distanceMeters(activity.getLocationLat(), activity.getLocationLng(), req.getLat(), req.getLng());
            if (distance > 500) {
                throw new BusinessException(40303, "不在活动地点附近");
            }
        }
        RegistrationEntity registration = registrationMapper.selectOne(Wrappers.<RegistrationEntity>lambdaQuery()
                .eq(RegistrationEntity::getActivityId, activityId)
                .eq(RegistrationEntity::getUserId, userId)
                .ne(RegistrationEntity::getStatus, "cancelled"));
        if (registration == null) {
            throw new BusinessException(40302, "未报名该活动");
        }
        if ("checked_in".equals(registration.getStatus())) {
            throw new BusinessException(40904, "您已签到");
        }
        registration.setStatus("checked_in");
        registration.setCheckedInAt(LocalDateTime.now());
        registrationMapper.updateById(registration);
    }

    @Override
    public Map<String, Object> qrcode(String activityId, String userId) {
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        if (!activity.getCreatorId().equals(userId)) {
            throw new BusinessException(40306, "只有活动发起人才能生成签到码");
        }
        String qrData = "activity:" + activityId + ":" + System.currentTimeMillis();
        activity.setCheckInQrCode(qrData);
        activity.setCheckInEnabled(true);
        activityMapper.updateById(activity);
        return Map.of("qr_code_url", qrCodeGenerator.toDataUri(qrData), "qr_data", qrData, "expires_at", LocalDateTime.now().plusHours(2).toString());
    }

    @Override
    public Map<String, Object> list(String activityId, String userId) {
        ActivityEntity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new BusinessException(40401, "活动不存在");
        }
        if (!activity.getCreatorId().equals(userId)) {
            throw new BusinessException(40306, "只有活动发起人才能查看签到列表");
        }
        List<Map<String, Object>> participants = registrationMapper.selectList(Wrappers.<RegistrationEntity>lambdaQuery()
                        .eq(RegistrationEntity::getActivityId, activityId))
                .stream()
                .map(item -> {
                    UserEntity user = userMapper.selectById(item.getUserId());
                    Map<String, Object> m = new java.util.HashMap<>();
                    m.put("user_id", item.getUserId());
                    m.put("nickname", user != null ? user.getNickname() : "");
                    m.put("avatar_url", user != null ? user.getAvatarUrl() : "");
                    m.put("registered_at", item.getCreatedAt() == null ? "" : item.getCreatedAt().toString());
                    m.put("checked_in", "checked_in".equals(item.getStatus()));
                    m.put("checked_in_at", item.getCheckedInAt() == null ? "" : item.getCheckedInAt().toString());
                    return m;
                })
                .toList();
        long totalRegistered = participants.size();
        long totalCheckedIn = participants.stream().filter(p -> (boolean) p.get("checked_in")).count();
        return Map.of(
                "items", participants,
                "stats", Map.of(
                        "total_registered", (int) totalRegistered,
                        "total_checked_in", (int) totalCheckedIn,
                        "check_in_rate", totalRegistered > 0 ? (double) Math.round((double) totalCheckedIn / totalRegistered * 100) / 100 : 0.0
                )
        );
    }
}
