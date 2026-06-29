package com.quju.admin.dto;

import jakarta.validation.constraints.NotBlank;

public class BanRequest {

    @NotBlank(message = "封禁原因不能为空")
    private String reason;

    private String expiresAt;   // ISO 8601, null = 永久

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getExpiresAt() { return expiresAt; }
    public void setExpiresAt(String expiresAt) { this.expiresAt = expiresAt; }
}
