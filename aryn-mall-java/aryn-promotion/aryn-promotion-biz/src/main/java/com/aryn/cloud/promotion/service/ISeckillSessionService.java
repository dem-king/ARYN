package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.SeckillSession;

import java.util.List;

public interface ISeckillSessionService extends IService<SeckillSession> {

	/**
	 * 根据活动ID查询场次
	 */
	List<SeckillSession> listByActivityId(String activityId);

	/**
	 * 状态流转：未开始→进行中、进行中→已结束
	 */
	void refreshStatus();
}