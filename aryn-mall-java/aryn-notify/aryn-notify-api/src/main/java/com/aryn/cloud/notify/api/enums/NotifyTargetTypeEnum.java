
package com.aryn.cloud.notify.api.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 群发目标类型枚举
 *
 * @author aryn
 * @since 2026/07/05
 */
@Getter
@AllArgsConstructor
public enum NotifyTargetTypeEnum {

	ALL_USER(1, "全部用户"),
	SPECIFIED_USER(2, "指定用户"),
	MEMBER_LEVEL(3, "指定会员等级"),
	TAG(4, "指定标签");

	private final Integer code;

	private final String desc;

}
