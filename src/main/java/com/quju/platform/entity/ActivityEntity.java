package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "activities", autoResultMap = true)
public class ActivityEntity {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String creatorId;
    private String title;
    private String description;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> tags;
    private String activityType;
    private String coverImageUrl;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private LocalDateTime registrationDeadline;
    private Integer maxParticipants;
    private Integer currentParticipants;
    private Integer minCreditScore;
    private Integer minAge;
    private String feeType;
    private BigDecimal feeAmount;
    private String city;
    private String locationName;
    private BigDecimal locationLat;
    private BigDecimal locationLng;
    private String status;
    private String aiReviewResult;
    private String reviewReason;
    private String reviewedBy;
    private LocalDateTime reviewedAt;
    @TableField("is_team_activity")
    private Boolean teamActivity;
    private String teamId;
    private String clonedFromId;
    private String checkInQrCode;
    private Boolean checkInEnabled;
    private Boolean checkInLocationRequired;
    /** 与用户的距离（米），由 nearby SQL 动态计算，非数据库列 */
    @TableField(exist = false)
    private BigDecimal distanceMeters;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
