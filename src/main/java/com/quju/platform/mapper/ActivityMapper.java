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
}
