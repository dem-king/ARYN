package com.aryn.cloud.promotion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.promotion.api.entity.SalesCustomerBindLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 客户换绑日志 Mapper（只追加）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Mapper
public interface SalesCustomerBindLogMapper extends BaseMapper<SalesCustomerBindLog> {

}
