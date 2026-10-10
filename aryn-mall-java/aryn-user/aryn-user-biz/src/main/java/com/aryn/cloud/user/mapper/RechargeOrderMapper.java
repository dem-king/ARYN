package com.aryn.cloud.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.user.api.entity.RechargeOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 充值订单
 *
 * @author 雨滴kian
 */
@Mapper
public interface RechargeOrderMapper extends BaseMapper<RechargeOrder> {

	/**
	 * 抢占式推进「待支付 → 已支付」。
	 *
	 * <p>支付回调、主动查单确认与 MQ 重投可能并发到达同一笔充值单，
	 * 用带状态条件的更新保证只有一次能拿到推进权，避免重复入账。
	 *
	 * @return 1=本次成功推进；0=订单非待支付
	 */
	@Update("""
			UPDATE recharge_order
			SET pay_status = '1',
				pay_order_no = #{payOrderNo},
				pay_time = #{payTime},
				update_time = NOW()
			WHERE id = #{id}
			  AND pay_status = '0'
			  AND del_flag = '0'
			""")
	int markPaidIfPending(@Param("id") String id, @Param("payOrderNo") String payOrderNo,
			@Param("payTime") LocalDateTime payTime);

}
