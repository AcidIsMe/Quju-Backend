package com.quju.platform.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChangePasswordReq {

    @NotBlank
    @JsonProperty("old_password")
    private String oldPassword;

    @NotBlank
    @Size(min = 8, max = 72)
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "新密码需包含字母和数字")
    @JsonProperty("new_password")
    private String newPassword;
}
