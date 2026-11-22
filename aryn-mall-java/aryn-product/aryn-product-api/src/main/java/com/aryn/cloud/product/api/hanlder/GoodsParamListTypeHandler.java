package com.aryn.cloud.product.api.hanlder;

import com.alibaba.fastjson2.JSON;
import com.aryn.cloud.product.api.entity.GoodsParam;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

import java.util.List;

/** 仅在商品参数字段及详情结果映射中使用。 */
public class GoodsParamListTypeHandler extends AbstractJsonTypeHandler<List<GoodsParam>> {

	public GoodsParamListTypeHandler(Class<?> type) {
		super(type);
	}

	@Override
	public List<GoodsParam> parse(String json) {
		return JSON.parseArray(json, GoodsParam.class);
	}

	@Override
	public String toJson(List<GoodsParam> value) {
		return JSON.toJSONString(value);
	}

}
