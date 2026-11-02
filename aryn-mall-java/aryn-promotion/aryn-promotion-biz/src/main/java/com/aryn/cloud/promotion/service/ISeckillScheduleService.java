package com.aryn.cloud.promotion.service;

import com.aryn.cloud.promotion.api.vo.SeckillReminderConfigVO;

import java.util.List;
import java.util.Set;

/**
 * 秒杀每日排期：场次生成、库存顺延与开抢提醒。
 */
public interface ISeckillScheduleService {

	/**
	 * 为当前租户所有每日重复活动生成未来 N 天场次（幂等）
	 */
	int generateSessions(int days);

	/**
	 * 为指定活动生成未来 N 天场次（幂等；活动保存后立即生成）
	 */
	int generateForActivity(String activityId, int days);

	/**
	 * 按最新模板重建未来未开始的场次（保留已有订单/订阅的场次），然后生成未来 N 天
	 */
	int rebuildFutureSessions(String activityId);

	/**
	 * 场次结束后把未售库存顺延到当日下一场（每个场次只处理一次）
	 */
	int carryoverEndedSessions();

	/**
	 * 投递开抢前的微信订阅提醒
	 */
	int sendDueReminders();

	/**
	 * C 端提醒配置
	 */
	SeckillReminderConfigVO getReminderConfig();

	/**
	 * 订阅场次开抢提醒
	 */
	void subscribe(String sessionId, String mallUserId);

	/**
	 * 取消订阅
	 */
	void unsubscribe(String sessionId, String mallUserId);

	/**
	 * 是否已订阅
	 */
	boolean isSubscribed(String sessionId, String mallUserId);

	/**
	 * 批量查询已订阅的场次ID
	 */
	Set<String> subscribedSessionIds(List<String> sessionIds, String mallUserId);
}
