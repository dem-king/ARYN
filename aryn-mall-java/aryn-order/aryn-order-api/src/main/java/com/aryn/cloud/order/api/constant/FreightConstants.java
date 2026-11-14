package com.aryn.cloud.order.api.constant;

/**
 * 运费规则常量
 *
 * <p>与 {@code freight_rule} 表取值、订单 {@code freight_free_reason} 落库口径一一对应，
 * 前端按原因码映射文案，不在后端拼中文。
 *
 * @author aryn
 * @since 2026/11/12
 */
public interface FreightConstants {

	/** 购买场景/配送方式：全部 */
	String ALL = "0";

	/** 规则启用 */
	String ENABLED = "1";

	/** 免邮原因：满额包邮 */
	String REASON_THRESHOLD = "THRESHOLD";

	/** 免邮原因：会员免邮权益 */
	String REASON_MEMBER = "MEMBER";

	/** 免邮原因：上门自提 */
	String REASON_PICKUP = "PICKUP";

	/** 免邮原因：公司配送（到船） */
	String REASON_INTERNAL = "INTERNAL";

	/** 未减免（含未配置规则、未达门槛仍收费） */
	String REASON_NONE = "NONE";

}
