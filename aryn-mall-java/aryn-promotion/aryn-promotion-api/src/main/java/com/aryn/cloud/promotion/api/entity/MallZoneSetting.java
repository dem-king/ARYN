package com.aryn.cloud.promotion.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 会员/出清专区设置：开关、默认折扣率、成本兜底倍率与排序。
 *
 * <p>每个租户每个专区一条（tenant_id + zone_type 唯一）；开关关闭时同步服务清空
 * 对应系统托管活动的 rules，C 端专区不再产生身份价。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("mall_zone_setting")
public class MallZoneSetting extends Model<MallZoneSetting> {

	public static final String ZONE_MEMBER = "member";

	public static final String ZONE_CLEARANCE = "clearance";

	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	private String tenantId;

	/** member / clearance */
	private String zoneType;

	/** 专区开关：1开 0关 */
	private String enabled;

	/** 默认折扣率：商品未配置时兜底 */
	private BigDecimal defaultDiscountRate;

	/** 会员价成本兜底倍率：未显式配置下限时，下限=成本×倍率；空=不启用 */
	private BigDecimal costFloorMultiplier;

	/** 排序：default / price_asc / discount_desc */
	private String sortMode;

	/** 上次系统活动同步时间（G19：同步状态可观测） */
	private LocalDateTime lastSyncTime;

	/** 上次同步结果：SUCCESS / FAILED / UNKNOWN */
	private String lastSyncStatus;

	/** 上次同步失败原因（截断至 500 字符） */
	private String lastSyncError;

	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

}
