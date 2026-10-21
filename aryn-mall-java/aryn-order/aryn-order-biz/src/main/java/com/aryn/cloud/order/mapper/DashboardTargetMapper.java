package com.aryn.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.order.api.entity.DashboardTarget;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理端概览月度目标。
 */
@Mapper
public interface DashboardTargetMapper extends BaseMapper<DashboardTarget> {

}
