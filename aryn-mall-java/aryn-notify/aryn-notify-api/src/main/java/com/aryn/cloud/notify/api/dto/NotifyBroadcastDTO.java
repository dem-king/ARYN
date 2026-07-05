
package com.aryn.cloud.notify.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;

/**
 * 群发消息DTO
 *
 * @author aryn
 * @since 2026/07/05
 */
@Data
@Schema(description = "群发消息DTO")
public class NotifyBroadcastDTO implements Serializable {

	@Schema(description = "主键")
	private String id;

	@Schema(description = "群发标题")
	private String title;

	@Schema(description = "群发内容")
	private String content;

	@Schema(description = "消息类型")
	private Integer notifyType;

	@Schema(description = "目标：1-全部用户 2-指定用户 3-指定会员等级 4-指定标签")
	private Integer targetType;

	@Schema(description = "目标ID列表（JSON数组字符串）")
	private String targetIds;

	@Schema(description = "跳转类型")
	private Integer jumpType;

	@Schema(description = "跳转地址")
	private String jumpUrl;

	@Schema(description = "操作人ID（后端注入）")
	private String operatorId;

}
