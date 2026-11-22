package com.aryn.cloud.product.api.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 商品档案中的一条营养成分（每 100g / 每份，按运营录入口径展示）。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsNutrition implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/** 项目名，如能量、蛋白质、碳水化合物 */
	private String name;

	/** 数值，保留运营录入的原始文本，避免前端二次换算 */
	private String amount;

	/** 单位，如 kJ、g、mg */
	private String unit;

	/** 营养素参考值百分比，如 3%；无数据时为空 */
	private String nrv;
}
