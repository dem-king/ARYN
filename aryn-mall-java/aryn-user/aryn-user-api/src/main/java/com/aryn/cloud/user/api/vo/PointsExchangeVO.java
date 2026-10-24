package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * C 端积分兑换项视图：兑换配置 + 券模板展示信息 + 本人已兑次数。
 */
@Data
@Schema(description = "积分兑换项")
public class PointsExchangeVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "兑换项ID")
	private String id;

	@Schema(description = "标题")
	private String title;

	@Schema(description = "图标URL")
	private String iconUrl;

	@Schema(description = "所需积分")
	private Integer pointsCost;

	@Schema(description = "每人限兑次数，0=不限")
	private Integer perUserLimit;

	@Schema(description = "券名称（模板信息缺失时回落标题）")
	private String couponName;

	@Schema(description = "券类型：1-满减券；2-折扣券")
	private String couponType;

	@Schema(description = "满减券面额（元）")
	private BigDecimal couponAmount;

	@Schema(description = "折扣券折数（如 8.5）")
	private BigDecimal couponDiscount;

	@Schema(description = "使用门槛（元，0=无门槛）")
	private BigDecimal couponThreshold;

	@Schema(description = "券模板剩余库存（null=未取到）")
	private Integer couponRemainNum;

	@Schema(description = "本人已兑次数")
	private Integer exchangedCount;

	@Schema(description = "是否可兑：false 时前端置灰并提示原因")
	private boolean exchangeable;

	@Schema(description = "不可兑原因（余额不足/已达上限/已抢完/已下架）")
	private String unexchangeableReason;

}
