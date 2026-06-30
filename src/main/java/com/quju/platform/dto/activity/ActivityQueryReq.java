package com.quju.platform.dto.activity;

import com.quju.platform.dto.common.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityQueryReq extends PageQuery {

    private String q;
    private String city;
    private String type;
    private String activityTypes;
    private String feeType;
    private String status;
    private BigDecimal lat;
    private BigDecimal lng;
    private Integer radius;
    private Integer radiusMeters;
    private Integer maxDistance;
    private LocalDateTime startFrom;
    private LocalDateTime startTo;
    private LocalDateTime startAfter;
    private LocalDateTime startBefore;
    // 地图边界框（US17）
    private BigDecimal swLat;
    private BigDecimal swLng;
    private BigDecimal neLat;
    private BigDecimal neLng;

    public Integer effectiveRadiusMeters() {
        if (maxDistance != null) {
            return maxDistance;
        }
        if (radius != null) {
            return radius;
        }
        return radiusMeters == null ? 5000 : radiusMeters;
    }

    public LocalDateTime effectiveStartFrom() {
        return startAfter == null ? startFrom : startAfter;
    }

    public LocalDateTime effectiveStartTo() {
        return startBefore == null ? startTo : startBefore;
    }
}
