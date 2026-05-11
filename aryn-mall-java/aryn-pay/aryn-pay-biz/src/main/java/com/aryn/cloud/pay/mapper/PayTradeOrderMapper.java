
package com.aryn.cloud.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.pay.api.entity.PayTradeOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 支付订单
 *
 * @author 雨滴kian
 * @date 2022/6/16
 */
@Mapper
public interface PayTradeOrderMapper extends BaseMapper<PayTradeOrder> {

}
