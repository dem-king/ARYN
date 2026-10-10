package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * C端充值订单VO
 *
 * <p>只暴露展示字段：充值订单实体带 createBy/tenantId/delFlag 等内部字段，
 * 不能直接回给 C 端。
 *
 * @author 雨滴kian
 */
@Data
public class AppRechargeOrderVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "充值订单号")
	private String orderNo;

	@Schema(description = "支付状态：0-待支付；1-已支付；2-已取消")
	private String payStatus;

	@Schema(description = "充值金额")
	private BigDecimal rechargeAmount;

	@Schema(description = "赠送金额")
	private BigDecimal giftAmount;

	@Schema(description = "赠送积分")
	private Integer giftPoint;

	@Schema(description = "支付时间")
	private LocalDateTime payTime;

	/**
	 * 渠道交易状态：由结果核对接口在向渠道查单后回填。
	 *
	 * <p>存在的意义是让 C 端能区分「还在确认中」与「渠道已关闭/支付失败」：
	 * 两者在本地的 payStatus 都是待支付，但前者应继续等待，后者应引导重新充值。
	 * 取值同微信 tradeState（SUCCESS/NOTPAY/USERPAYING/CLOSED/REVOKED/PAYERROR）。
	 */
	@Schema(description = "渠道交易状态（查单核对后回填）")
	private String channelTradeState;

	public static AppRechargeOrderVO from(com.aryn.cloud.user.api.entity.RechargeOrder source) {
		AppRechargeOrderVO vo = new AppRechargeOrderVO();
		vo.setOrderNo(source.getOrderNo());
		vo.setPayStatus(source.getPayStatus());
		vo.setRechargeAmount(source.getRechargeAmount());
		vo.setGiftAmount(source.getGiftAmount());
		vo.setGiftPoint(source.getGiftPoint());
		vo.setPayTime(source.getPayTime());
		return vo;
	}

}
