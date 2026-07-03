package com.quju.platform.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.quju.platform.entity.ActivityEntity;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ActivityMapper extends BaseMapper<ActivityEntity> {

    List<ActivityEntity> searchNearby(@Param("lat") BigDecimal lat,
                                      @Param("lng") BigDecimal lng,
                                      @Param("radiusMeters") Integer radiusMeters,
                                      @Param("limit") Integer limit);

    ActivityEntity selectByIdForUpdate(@Param("id") String id);

    /** 关键词搜索（按相关度排序：标题 > 标签 > 简介 > 发布时间） */
    List<ActivityEntity> searchWithRelevance(@Param("q") String q,
                                             @Param("city") String city,
                                             @Param("feeType") String feeType,
                                             @Param("activityTypes") String activityTypes,
                                             @Param("startAfter") String startAfter,
                                             @Param("startBefore") String startBefore,
                                             @Param("limit") Integer limit);

    /** 地图边界框查询 + 轻量数据，含可选距离计算 */
    List<ActivityEntity> searchMapBox(@Param("swLat") BigDecimal swLat,
                                      @Param("swLng") BigDecimal swLng,
                                      @Param("neLat") BigDecimal neLat,
                                      @Param("neLng") BigDecimal neLng,
                                      @Param("lat") BigDecimal lat,
                                      @Param("lng") BigDecimal lng,
                                      @Param("limit") Integer limit);
}
