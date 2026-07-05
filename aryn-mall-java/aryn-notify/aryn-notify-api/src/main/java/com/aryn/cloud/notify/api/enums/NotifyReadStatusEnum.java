
package com.aryn.cloud.notify.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 消息已读状态枚举
 *
 * @author aryn
 * @since 2026/07/05
 */
@Getter
@AllArgsConstructor
public enum NotifyReadStatusEnum {

	UNREAD("0", "未读"),
	READ("1", "已读");

	private final String code;

	private final String desc;

}
