package com.quju.platform.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName(value = "users", autoResultMap = true)
public class UserEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String email;
    private String passwordHash;
    private String nickname;
    private String avatarUrl;
    private String gender;
    private LocalDate birthday;
    private String bio;
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> interestTags;
    private String role;
    private String status;
    private Integer creditScore;
    private BigDecimal locationLat;
    private BigDecimal locationLng;
    private LocalDateTime locationUpdatedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
