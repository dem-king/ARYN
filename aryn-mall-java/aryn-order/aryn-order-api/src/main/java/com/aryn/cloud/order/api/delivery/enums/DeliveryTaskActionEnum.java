package com.aryn.cloud.order.api.delivery.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 商城配送任务审计动作。
 */
@Getter
@RequiredArgsConstructor
public enum DeliveryTaskActionEnum {

	CREATE("创建任务"),
	ASSIGN("首次派单"),
	REASSIGN("改派或再次配送"),
	START_PICKING("开始配货"),
	CHECK_ITEM("核对商品"),
	PICKUP("确认取货"),
	DELIVER("确认送达"),
	REPORT_EXCEPTION("上报异常"),
	MARK_RETURN_PENDING("进入待退回"),
	CONFIRM_RETURN("确认退回仓库"),
	CLOSE("关闭任务");

	private final String description;

}
