package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.SalesRefundRecord;

/**
 * 销售业绩退款幂等服务
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
public interface ISalesRefundRecordService extends IService<SalesRefundRecord> {

	/**
	 * 按退款业务号查询
	 * @param refundNo 退款业务号
	 * @return 记录，无则 null
	 */
	SalesRefundRecord getByRefundNo(String refundNo);

	/**
	 * 重开该订单下已放弃应用的退款记录（applied=1 → 0）
	 * <p>
	 * 退款早于支付事件超过重放等待期时记录会被置为已应用放弃；若业绩单延迟生成，
	 * 支付事件到达后需重开这些记录再补应用，避免漏扣提成。
	 * @param bizOrderId 业务订单ID
	 * @return 重开条数
	 */
	int reopenAbandonedByBizOrderId(String bizOrderId);

}
