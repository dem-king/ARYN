
package com.aryn.cloud.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.pay.api.entity.PayRefundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 退款订单
 *
 * @author 雨滴kian
 * @date 2022/6/16
 */
@Mapper
public interface PayRefundOrderMapper extends BaseMapper<PayRefundOrder> {

	int markRefundedIfProcessing(@Param("tenantId") String tenantId, @Param("id") String id,
			@Param("channelRefundNo") String channelRefundNo,
			@Param("refundSuccessTime") LocalDateTime refundSuccessTime);

}
