package com.quju.platform.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class MerchantApplyReq {

    @Email
    @NotBlank
    private String email;

    @NotBlank
    @Size(min = 8, max = 72)
    private String password;

    @NotBlank
    private String nickname;

    @NotBlank
    @JsonProperty("merchant_name")
    private String merchantName;

    @JsonProperty("activity_domains")
    private List<String> activityDomains;

    @JsonProperty("license_image_url")
    private String licenseImageUrl;
}
