package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.user.api.entity.MemberPaidOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 付费会员订单
 *
 * @author aryn
 */
@Mapper
public interface MemberPaidOrderMapper extends BaseMapper<MemberPaidOrder> {

}