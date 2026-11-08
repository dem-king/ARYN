package com.aryn.cloud.order.mapper;

import com.aryn.cloud.order.api.entity.ReplenishDirectImportRow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/** 清单直下单导入解析行持久层。 */
@Mapper
public interface ReplenishDirectImportRowMapper extends BaseMapper<ReplenishDirectImportRow> {
}
