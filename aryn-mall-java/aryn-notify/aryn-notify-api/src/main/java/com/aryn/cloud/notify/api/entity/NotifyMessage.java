
package com.aryn.cloud.notify.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 消息记录
 *
 * @author aryn
 * @since 2026/07/05
 */
@Data
@Schema(description = "消息记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "notify_message")
public class NotifyMessage extends Model<NotifyMessage> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "接收用户ID")
	private String userId;

	@Schema(description = "消息类型：1-订单 2-支付 3-物流 4-营销 5-系统 6-社交")
	private Integer notifyType;

	@Schema(description = "消息标题")
	private String title;

	@Schema(description = "消息内容")
	private String content;

	@Schema(description = "业务类型（order/pay/refund...）")
	private String bizType;

	@Schema(description = "业务ID（订单号/退款单号等）")
	private String bizId;

	@Schema(description = "跳转类型：0-不跳转 1-订单详情 2-商品详情 3-活动页 4-自定义链接")
	private Integer jumpType;

	@Schema(description = "跳转地址")
	private String jumpUrl;

	@Schema(description = "已读状态：0-未读 1-已读")
	private String readStatus;

	@Schema(description = "阅读时间")
	private LocalDateTime readTime;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

	@Schema(description = "租户id")
	private String tenantId;

}
