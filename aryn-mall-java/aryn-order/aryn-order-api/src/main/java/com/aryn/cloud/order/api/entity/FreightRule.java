package com.aryn.cloud.order.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单级运费规则（满额包邮 / 基础运费）
 *
 * <p>按「购买场景 + 配送方式 + 省份」匹配，{@code priority} 数值大者优先；
 * 门槛基数只统计 {@code goods_spu.freight_type='2'} 的明细成交金额
 * （活动价后、券与积分前，不含运费），{@code freight_type='0'} 商品永远包邮、
 * {@code '1'} 商品独立计费且不参与门槛，存量行为不变。
 *
 * <p>配送方式 2 上门自提 / 4 公司配送（到船）不进入运费计算。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Data
@Schema(description = "订单级运费规则")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "freight_rule")
public class FreightRule extends Model<FreightRule> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "规则名称")
	private String ruleName;

	@Schema(description = "购买场景：0-全部；1-海员个人；2-船供采购；3-企业采购")
	private String bizScene;

	@Schema(description = "配送方式：1-普通快递；3-公司配送（岸上）")
	private String deliveryWay;

	@Schema(description = "适用省份编码，英文逗号分隔；空=全国")
	private String provinceCodes;

	@Schema(description = "包邮门槛（元）；0=不设门槛")
	private BigDecimal thresholdAmount;

	@Schema(description = "未达门槛时收取的基础运费（元）")
	private BigDecimal baseFreight;

	@Schema(description = "基础运费封顶（元，不含独立计费商品）；NULL=不封顶")
	private BigDecimal maxFreight;

	@Schema(description = "优先级，数值大者优先")
	private Integer priority;

	@Schema(description = "启用状态：1启用 0禁用")
	private String enabled;

	@Schema(description = "运营备注")
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
