package com.quju.platform.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TokenRefreshReq {

    @NotBlank
    @JsonProperty("refresh_token")
    private String refreshToken;
}
