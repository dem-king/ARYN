package com.aryn.cloud.product.api.hanlder;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.handlers.AbstractJsonTypeHandler;

import java.util.List;

/**
 * 服务承诺标签（code 数组）的 JSON 转换。
 *
 * <p>与商品参数的 {@link GoodsParamListTypeHandler} 同款写法；空串按 null 处理，
 * 避免存量行是空字符串时解析失败。
 */
public class ServiceTagListTypeHandler extends AbstractJsonTypeHandler<List<String>> {

	public ServiceTagListTypeHandler(Class<?> type) {
		super(type);
	}

	@Override
	public List<String> parse(String json) {
		return json == null || json.isBlank() ? null : JSON.parseArray(json, String.class);
	}

	@Override
	public String toJson(List<String> value) {
		return JSON.toJSONString(value);
	}

}
