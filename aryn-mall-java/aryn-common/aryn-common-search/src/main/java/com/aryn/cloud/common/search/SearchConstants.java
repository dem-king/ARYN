/*
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */
package com.aryn.cloud.common.search;

/**
 * 搜索索引常量定义
 * <p>
 * 定义 Meilisearch 索引名称、主键及字段常量，供业务模块统一引用。
 * </p>
 *
 * @author aryn
 */
public interface SearchConstants {

	/**
	 * SPU 索引名称
	 */
	String SPU_INDEX = "spu_index";

	/**
	 * SPU 索引主键字段
	 */
	String SPU_INDEX_PRIMARY_KEY = "id";

	/**
	 * SPU 索引字段：主键
	 */
	String SPU_FIELD_ID = "id";

	/**
	 * SPU 索引字段：商品名称
	 */
	String SPU_FIELD_NAME = "name";

	/**
	 * SPU 索引字段：副标题
	 */
	String SPU_FIELD_SUBTITLE = "subtitle";

	/**
	 * SPU 索引字段：关键词
	 */
	String SPU_FIELD_KEYWORD = "keyword";

	/**
	 * SPU 索引字段：分类ID
	 */
	String SPU_FIELD_CATEGORY_ID = "category_id";

	/**
	 * SPU 索引字段：分类名称
	 */
	String SPU_FIELD_CATEGORY_NAME = "category_name";

	/**
	 * SPU 索引字段：品牌ID
	 */
	String SPU_FIELD_BRAND_ID = "brand_id";

	/**
	 * SPU 索引字段：价格
	 */
	String SPU_FIELD_PRICE = "price";

	/**
	 * SPU 索引字段：销量
	 */
	String SPU_FIELD_SALES = "sales";

	/**
	 * SPU 索引字段：状态
	 */
	String SPU_FIELD_STATUS = "status";

	/**
	 * SPU 索引字段：租户ID
	 */
	String SPU_FIELD_TENANT_ID = "tenant_id";

	/**
	 * SPU 索引字段：创建时间
	 */
	String SPU_FIELD_CREATE_TIME = "create_time";

}