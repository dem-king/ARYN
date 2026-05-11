
package com.aryn.cloud.common.core.util;

import cn.hutool.core.util.IdUtil;

/**
 * 全局唯一ID工具类
 *
 * @author 雨滴kian
 * @since 2022/3/14 16:24
 */
public class SnowflakeIdUtils {

	/**
	 * 订单编号
	 *
	 * @author 雨滴kian
	 * @date 2022/7/2
	 * @return: java.lang.String
	 */
	public static String orderNo() {
		return IdUtil.getSnowflake(1, 1).nextIdStr();
	}

	/**
	 * 退款单号
	 *
	 * @author 雨滴kian
	 * @date 2022/7/2
	 * @return: java.lang.String
	 */
	public static String refundOrderNo() {
		return IdUtil.getSnowflake(1, 2).nextIdStr();
	}

}
