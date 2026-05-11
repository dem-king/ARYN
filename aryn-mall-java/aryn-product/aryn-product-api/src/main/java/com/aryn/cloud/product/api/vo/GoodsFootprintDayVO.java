/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * <p>
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */
package com.aryn.cloud.product.api.vo;

import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.annotation.TableField;
import com.aryn.cloud.common.myabtis.handler.JsonArrayTypeHandler;
import lombok.Data;

@Data
public class GoodsFootprintDayVO {

	private String browseDate;

	@TableField(typeHandler = JsonArrayTypeHandler.class)
	private JSONArray items;

}
