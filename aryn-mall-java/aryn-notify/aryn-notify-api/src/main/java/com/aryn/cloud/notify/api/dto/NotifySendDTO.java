
package com.aryn.cloud.notify.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.util.Map;

/**
 * 发送消息DTO
 *
 * @author aryn
 * @since 2026/07/05
 */
@Data
@Accessors(chain = true)
@Schema(description = "发送消息DTO")
public class NotifySendDTO implements Serializable {

	@Schema(description = "接收用户ID")
	private String userId;

	@Schema(description = "消息类型（无模板时必填）")
	private Integer notifyType;

	@Schema(description = "消息标题（无模板时必填）")
	private String title;

	@Schema(description = "消息内容（无模板时必填）")
	private String content;

	@Schema(description = "模板编码（使用模板渲染时必填）")
	private String templateCode;

	@Schema(description = "模板变量参数")
	private Map<String, String> params;

	@Schema(description = "业务类型（order/pay/refund...）")
	private String bizType;

	@Schema(description = "业务ID（订单号/退款单号等）")
	private String bizId;

	@Schema(description = "跳转类型：0-不跳转 1-订单详情 2-商品详情 3-活动页 4-自定义链接")
	private Integer jumpType;

	@Schema(description = "跳转地址")
	private String jumpUrl;

	@Schema(description = "租户ID")
	private String tenantId;

}
