package com.quju.platform.dto.registration;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CheckInReq {

    @NotBlank
    @JsonProperty("qr_data")
    private String qrData;

    private BigDecimal lat;
    private BigDecimal lng;
}
