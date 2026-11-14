package com.aryn.cloud.promotion.api.entity;

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
 * 价格政策（协议价）。
 *
 * <p>B2B 分层价格体系的最高优先级价格层：按采购主体（buyer_org）/船舶（vessel）/
 * 客群标签圈定适用对象，指定 SKU 的专属合同价（固定价或折扣率）。
 * 长期有效（不走活动发布），命中 SKU 的成交基价被锁定，
 * 阶梯价/身份价/档期特供一律不覆盖。SKU 明细见 {@link PricePolicyItem}。
 *
 * <p>同 SKU 多政策命中：priority 取大，同级取更低价格（引擎 {@code applyPolicyPrices}）。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Data
@Schema(description = "价格政策（协议价）")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "price_policy")
public class PricePolicy extends Model<PricePolicy> {

	/** 适用对象：指定采购主体 */
	public static final String APPLY_BUYER_ORG = "1";

	/** 适用对象：指定船舶 */
	public static final String APPLY_VESSEL = "2";

	/** 适用对象：客群标签（member_tag.id） */
	public static final String APPLY_TAG = "3";

	/** 价格模式：固定协议价 */
	public static final String MODE_FIXED_PRICE = "1";

	/** 价格模式：折扣率（对售价，如 0.9000=9折） */
	public static final String MODE_RATE = "2";

	/** 状态：草稿 */
	public static final String STATUS_DRAFT = "0";

	/** 状态：生效 */
	public static final String STATUS_ACTIVE = "1";

	/** 状态：停用 */
	public static final String STATUS_DISABLED = "2";

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "政策名称")
	private String policyName;

	@Schema(description = "适用对象：1指定采购主体 2指定船舶 3客群标签")
	private String applyType;

	@Schema(description = "适用对象ID集合（主体/船舶/标签ID逗号分隔）")
	private String applyValue;

	@Schema(description = "价格模式：1固定协议价 2折扣率")
	private String priceMode;

	@Schema(description = "优先级（同SKU多政策命中取大，同级取低价）")
	private Integer priority;

	@Schema(description = "生效开始")
	private LocalDateTime startTime;

	@Schema(description = "生效结束")
	private LocalDateTime endTime;

	@Schema(description = "状态：0草稿 1生效 2停用")
	private String status;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "租户ID")
	private String tenantId;

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

}
