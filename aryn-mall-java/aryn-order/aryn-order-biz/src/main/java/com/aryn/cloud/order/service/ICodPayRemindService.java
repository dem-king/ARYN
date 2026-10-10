package com.aryn.cloud.order.service;

import java.util.List;

/**
 * 货到付款（COD）收款预警：扫描收货后超时未收款的订单并向买卖双端发送站内信提醒。
 *
 * @author Aetheryn
 * @date 2026/10/03
 */
public interface ICodPayRemindService {

	/**
	 * 对单个租户执行一轮收款预警扫描（须在租户上下文内调用）。
	 * @param tenantId 租户
	 * @return 本次实际发出的站内信命令条数
	 */
	int remindTenant(String tenantId);

	/**
	 * 解析提醒时间点配置（收货后小时数，逗号分隔）。
	 * @param configValue 配置值；null 视为默认 72,168（兼容存量 Redis 缓存），空串表示关闭
	 * @return 升序去重后的正整数小时列表；关闭或全部非法时返回空列表
	 */
	List<Integer> parseRemindHours(String configValue);

}
