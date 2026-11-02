package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.SeckillSubscribe;

import java.util.List;
import java.util.Set;

public interface ISeckillSubscribeService extends IService<SeckillSubscribe> {

	/**
	 * 订阅场次开抢提醒（已存在则复用/复活）
	 */
	boolean subscribe(String activityId, String sessionId, String mallUserId, String templateId);

	/**
	 * 取消订阅
	 */
	boolean unsubscribe(String sessionId, String mallUserId);

	/**
	 * 是否已订阅（待发送或已投递）
	 */
	boolean isSubscribed(String sessionId, String mallUserId);

	/**
	 * 批量查询已订阅的场次ID
	 */
	Set<String> subscribedSessionIds(List<String> sessionIds, String mallUserId);

	/**
	 * 查询场次下待发送的订阅
	 */
	List<SeckillSubscribe> listPendingBySession(String sessionId);

	/**
	 * 抢占订阅行（待发送→已投递），保证多实例只发一次
	 */
	boolean claim(String id);

	/**
	 * 标记发送失败
	 */
	void markFailed(String id, String reason);
}
