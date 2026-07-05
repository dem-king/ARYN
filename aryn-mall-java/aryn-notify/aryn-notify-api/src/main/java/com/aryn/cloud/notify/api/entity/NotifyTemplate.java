
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
 * 消息模板
 *
 * @author aryn
 * @since 2026/07/05
 */
@Data
@Schema(description = "消息模板")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "notify_template")
public class NotifyTemplate extends Model<NotifyTemplate> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "模板编码（唯一）")
	private String templateCode;

	@Schema(description = "模板名称")
	private String templateName;

	@Schema(description = "消息类型：1-订单 2-支付 3-物流 4-营销 5-系统 6-社交")
	private Integer notifyType;

	@Schema(description = "消息标题（支持变量 ${var}）")
	private String title;

	@Schema(description = "消息内容（支持变量 ${var}）")
	private String content;

	@Schema(description = "跳转类型：0-不跳转 1-订单详情 2-商品详情 3-活动页 4-自定义链接")
	private Integer jumpType;

	@Schema(description = "跳转地址")
	private String jumpUrl;

	@Schema(description = "状态：0-禁用 1-启用")
	private String status;

	@Schema(description = "备注")
	private String remark;

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
