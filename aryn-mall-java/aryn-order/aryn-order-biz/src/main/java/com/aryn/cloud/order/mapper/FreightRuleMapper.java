package com.aryn.cloud.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.order.api.entity.FreightRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 运费规则 Mapper
 *
 * @author aryn
 * @since 2026/11/12
 */
@Mapper
public interface FreightRuleMapper extends BaseMapper<FreightRule> {

}
