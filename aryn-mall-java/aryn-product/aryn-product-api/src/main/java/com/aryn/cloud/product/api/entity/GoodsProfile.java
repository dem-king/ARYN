package com.aryn.cloud.product.api.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 商品档案：商详「商品档案」弹层中的配料、营养、标准信息。
 *
 * <p>与 {@link GoodsParam} 分开：参数是首屏快速决策的短文本，档案是食品类目
 * 才需要展开的深层信息；两者不重复搬运。</p>
 */
@Data
@NoArgsConstructor
public class GoodsProfile implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	/** 配料表，纯文本；非食品类目为空 */
	private String ingredients;

	/** 营养成分表；空数组/空值时不出现该段 */
	private List<GoodsNutrition> nutrition;

	/** 过敏原提示，纯文本 */
	private String allergens;

	/** 执行标准，纯文本 */
	private String standard;

	/** 认证或品质保障标签，如绿色食品、地理标志；按运营录入 */
	private List<String> certifications;
}
