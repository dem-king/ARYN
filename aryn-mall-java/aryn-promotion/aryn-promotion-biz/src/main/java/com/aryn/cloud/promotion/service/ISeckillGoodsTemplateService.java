package com.aryn.cloud.promotion.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.promotion.api.entity.SeckillGoodsTemplate;

import java.util.List;

public interface ISeckillGoodsTemplateService extends IService<SeckillGoodsTemplate> {

	/**
	 * 查询排期模板下的商品
	 */
	List<SeckillGoodsTemplate> listByTemplateId(String templateId);

	/**
	 * 查询多个排期模板下的商品
	 */
	List<SeckillGoodsTemplate> listByTemplateIds(List<String> templateIds);

	/**
	 * 逻辑删除排期模板下的商品
	 */
	void removeByTemplateId(String templateId);
}
