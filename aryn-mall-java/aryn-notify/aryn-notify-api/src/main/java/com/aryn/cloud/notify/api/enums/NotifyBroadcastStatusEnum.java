
package com.aryn.cloud.notify.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 群发状态枚举
 *
 * @author aryn
 * @since 2026/07/05
 */
@Getter
@AllArgsConstructor
public enum NotifyBroadcastStatusEnum {

	WAITING("0", "待发送"),
	SENDING("1", "发送中"),
	COMPLETED("2", "已完成"),
	CANCELED("3", "已取消");

	private final String code;

	private final String desc;

}
