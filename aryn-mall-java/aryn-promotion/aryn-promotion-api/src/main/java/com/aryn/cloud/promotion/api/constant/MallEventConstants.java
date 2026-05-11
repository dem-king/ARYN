package com.aryn.cloud.promotion.api.constant;

/**
 * 商城活动常量
 *
 * @author 雨滴kian
 * @date 2022/6/8
 */
public interface MallEventConstants {

	/**
	 * 可用范围：1.全部商品；2.指定商品；
	 */
	String USE_RANGE_1 = "1";

	String USE_RANGE_2 = "2";

	/**
	 * 优惠券类型：1.满减券；2.折扣券；
	 */
	String COUPON_TYPE_1 = "1";

	String COUPON_TYPE_2 = "2";

	/**
	 * 分销配置状态：0启用 1禁用
	 */
	String DISTRIBUTION_CONFIG_STATUS_ENABLE = "0";

	String DISTRIBUTION_CONFIG_STATUS_DISABLE = "1";

	/**
	 * 分销用户状态：0启用 1禁用
	 */
	String DISTRIBUTION_USER_STATUS_ENABLE = "0";

	String DISTRIBUTION_USER_STATUS_DISABLE = "1";

	/**
	 * 默认佣金比例（分销配置不存在时的兜底值）
	 */
	String DEFAULT_COMMISSION_RATE = "0.10";

	/**
	 * 默认二级佣金比例
	 */
	String DEFAULT_COMMISSION_RATE_LEVEL2 = "0.05";

	/**
	 * 提现单号前缀
	 */
	String WITHDRAW_NO_PREFIX = "DW";

}
