package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.SeckillSession;
import com.aryn.cloud.promotion.api.vo.SeckillSessionVO;

import java.util.List;

public interface ISeckillSessionService extends IService<SeckillSession> {

	/**
	 * 根据活动ID查询场次
	 */
	List<SeckillSession> listByActivityId(String activityId);

	/**
	 * 管理端场次分页查询（跨活动，带活动名称；供装修器秒杀场次选择器与按ID回填）
	 * @param ids 场次ID集合，非空时按ID过滤（此时调用方把 size 传成 ids 长度）
	 * @param sessionName 场次名称模糊匹配
	 */
	IPage<SeckillSessionVO> getAdminPage(Page page, List<String> ids, String sessionName);

	/**
	 * 状态流转：未开始→进行中、进行中→已结束
	 */
	void refreshStatus();
}