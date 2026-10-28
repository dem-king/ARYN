
package com.aryn.cloud.pay.mapper;

import com.baomidou.mybatisplus.annotation.InterceptorIgnore;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.aryn.cloud.pay.api.entity.PayRefundOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

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

	/**
	 * 待确认的支付宝退款单。任务按行回填租户上下文，查询本身必须跨租户。
	 */
	@Select("""
			SELECT r.*
			FROM pay_refund_order r
			INNER JOIN pay_trade_order t
			  ON t.out_trade_no = r.out_trade_no
			 AND t.tenant_id = r.tenant_id
			WHERE r.refund_status = '4'
			  AND r.del_flag = '0'
			  AND t.del_flag = '0'
			  AND t.trade_type IN ('ALI_JSAPI_PAY', 'ALI_H5_PAY', 'ALI_APP_PAY')
			ORDER BY r.create_time
			LIMIT #{limit}
			""")
	@InterceptorIgnore(tenantLine = "true")
	List<PayRefundOrder> selectPendingAlipayReconcile(@Param("limit") int limit);

}
