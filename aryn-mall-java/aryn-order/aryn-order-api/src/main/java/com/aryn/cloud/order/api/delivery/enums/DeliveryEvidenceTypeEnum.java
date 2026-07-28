package com.aryn.cloud.order.api.delivery.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商城配送履约凭证类型。
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryEvidenceTypeEnum {

	DELIVERED("送达凭证"),
	EXCEPTION("异常凭证"),
	RETURN("退回凭证");

	private final String description;

}
