package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.promotion.api.entity.SalesPayoutAdjustment;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提成负数结转 Mapper
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Mapper
public interface SalesPayoutAdjustmentMapper extends BaseMapper<SalesPayoutAdjustment> {

}
