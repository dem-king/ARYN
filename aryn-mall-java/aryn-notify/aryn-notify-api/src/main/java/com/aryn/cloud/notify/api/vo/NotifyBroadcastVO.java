
package com.aryn.cloud.notify.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 群发记录VO
 *
 * @author aryn
 * @since 2026/07/05
 */
@Data
@Schema(description = "群发记录VO")
public class NotifyBroadcastVO implements Serializable {

	@Schema(description = "主键")
	private String id;

	@Schema(description = "群发标题")
	private String title;

	@Schema(description = "群发内容")
	private String content;

	@Schema(description = "消息类型")
	private Integer notifyType;

	@Schema(description = "目标类型：1-全部用户 2-指定用户 3-指定会员等级 4-指定标签")
	private Integer targetType;

	@Schema(description = "目标ID列表")
	private String targetIds;

	@Schema(description = "总发送数")
	private Integer totalCount;

	@Schema(description = "成功数")
	private Integer successCount;

	@Schema(description = "状态：0-待发送 1-发送中 2-已完成 3-已取消")
	private String status;

	@Schema(description = "发送时间")
	private LocalDateTime sendTime;

	@Schema(description = "操作人ID")
	private String operatorId;

	@Schema(description = "跳转类型")
	private Integer jumpType;

	@Schema(description = "跳转地址")
	private String jumpUrl;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
