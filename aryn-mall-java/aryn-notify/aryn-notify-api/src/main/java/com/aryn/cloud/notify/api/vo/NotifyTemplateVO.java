
package com.aryn.cloud.notify.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息模板VO
 *
 * @author aryn
 * @since 2026/07/05
 */
@Data
@Schema(description = "消息模板VO")
public class NotifyTemplateVO implements Serializable {

	@Schema(description = "主键")
	private String id;

	@Schema(description = "模板编码")
	private String templateCode;

	@Schema(description = "模板名称")
	private String templateName;

	@Schema(description = "消息类型")
	private Integer notifyType;

	@Schema(description = "消息标题")
	private String title;

	@Schema(description = "消息内容")
	private String content;

	@Schema(description = "跳转类型")
	private Integer jumpType;

	@Schema(description = "跳转地址")
	private String jumpUrl;

	@Schema(description = "状态：0-禁用 1-启用")
	private String status;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
