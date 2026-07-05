
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
 * 群发记录
 *
 * @author aryn
 * @since 2026/07/05
 */
@Data
@Schema(description = "群发记录")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "notify_broadcast")
public class NotifyBroadcast extends Model<NotifyBroadcast> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "群发标题")
	private String title;

	@Schema(description = "群发内容")
	private String content;

	@Schema(description = "消息类型")
	private Integer notifyType;

	@Schema(description = "目标：1-全部用户 2-指定用户 3-指定会员等级 4-指定标签")
	private Integer targetType;

	@Schema(description = "目标ID列表（JSON数组）")
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

	@Schema(description = "跳转类型：0-不跳转 1-订单详情 2-商品详情 3-活动页 4-自定义链接")
	private Integer jumpType;

	@Schema(description = "跳转地址")
	private String jumpUrl;

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
