package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.dto.SeckillSessionTemplateDTO;
import com.aryn.cloud.promotion.api.entity.SeckillSessionTemplate;
import com.aryn.cloud.promotion.api.vo.SeckillSessionTemplateVO;

import java.util.List;

public interface ISeckillSessionTemplateService extends IService<SeckillSessionTemplate> {

	/**
	 * 查询活动下的排期模板
	 */
	List<SeckillSessionTemplate> listByActivityId(String activityId);

	/**
	 * 查询活动下的排期模板（含商品，管理端详情用）
	 */
	List<SeckillSessionTemplateVO> listVoByActivityId(String activityId);

	/**
	 * 覆盖保存活动的排期模板与商品模板（先逻辑删除旧数据）
	 */
	void saveTemplates(String activityId, List<SeckillSessionTemplateDTO> templates);

	/**
	 * 逻辑删除活动下的排期模板与商品模板
	 */
	void removeByActivityId(String activityId);
}
