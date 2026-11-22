package com.aryn.cloud.product.api.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/** 商品详情中的一个文本参数。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoodsParam implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String label;

	private String value;
}
