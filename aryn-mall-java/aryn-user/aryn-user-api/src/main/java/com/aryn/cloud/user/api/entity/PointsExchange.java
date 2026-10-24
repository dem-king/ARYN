package com.aryn.cloud.user.api.entity;

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
 * 积分兑换项：积分兑换优惠券（行业趋势是把积分包装成「兑券/兑权益」而非第二货币，
 * 兑出的券可定向品类、设门槛，补贴比直接抵现更可控——小象积分商城/叮咚朴朴兑券口径）。
 */
@Data
@Schema(description = "积分兑换项")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "points_exchange")
public class PointsExchange extends Model<PointsExchange> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "兑换项标题（如「5 元无门槛券」）")
	private String title;

	@Schema(description = "图标URL（可选）")
	private String iconUrl;

	@Schema(description = "所需积分")
	private Integer pointsCost;

	@Schema(description = "优惠券模板ID（coupon_info.id，兑换成功后发放到用户账户）")
	private String couponId;

	@Schema(description = "每人限兑次数，0=不限")
	private Integer perUserLimit;

	@Schema(description = "状态：0-启用；1-下架")
	private String status;

	@Schema(description = "排序号（小在前）")
	private Integer sortOrder;

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

	@Schema(description = "租户ID")
	private String tenantId;

}
