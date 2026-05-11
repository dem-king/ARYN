
package com.aryn.cloud.pay.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 退款状态：0.待退款；1.退款中；2.已退款；3.退款失败； */
@Getter
@AllArgsConstructor
public enum PayRefundOrderStatusEnum {

	/** 待退款 */
	STATUS_0("0", "待退款"),

	/** 退款中 */
	STATUS_1("1", "退款中"),

	/** 已退款 */
	STATUS_2("2", "已退款"),

	/** 退款失败 */
	STATUS_3("3", "退款失败"),
	/** 待确认 */
	STATUS_4("4", "待确认");

	/** 名称 */
	private final String code;

	/** 描述 */
	private final String desc;

}
