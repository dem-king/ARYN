
package com.aryn.cloud.notify.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息展示VO
 *
 * @author aryn
 * @since 2026/07/05
 */
@Data
@Schema(description = "消息展示VO")
public class NotifyMessageVO implements Serializable {

	@Schema(description = "主键")
	private String id;

	@Schema(description = "消息类型")
	private Integer notifyType;

	@Schema(description = "消息标题")
	private String title;

	@Schema(description = "消息内容")
	private String content;

	@Schema(description = "业务类型")
	private String bizType;

	@Schema(description = "业务ID")
	private String bizId;

	@Schema(description = "跳转类型")
	private Integer jumpType;

	@Schema(description = "跳转地址")
	private String jumpUrl;

	@Schema(description = "已读状态：0-未读 1-已读")
	private String readStatus;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
