package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.promotion.api.entity.SeckillGoodsTemplate;
import com.aryn.cloud.promotion.mapper.SeckillGoodsTemplateMapper;
import com.aryn.cloud.promotion.service.ISeckillGoodsTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillGoodsTemplateServiceImpl
		extends ServiceImpl<SeckillGoodsTemplateMapper, SeckillGoodsTemplate>
		implements ISeckillGoodsTemplateService {

	@Override
	public List<SeckillGoodsTemplate> listByTemplateId(String templateId) {
		return this.list(Wrappers.<SeckillGoodsTemplate>lambdaQuery()
				.eq(SeckillGoodsTemplate::getTemplateId, templateId));
	}

	@Override
	public List<SeckillGoodsTemplate> listByTemplateIds(List<String> templateIds) {
		if (CollectionUtils.isEmpty(templateIds)) {
			return List.of();
		}
		return this.list(Wrappers.<SeckillGoodsTemplate>lambdaQuery()
				.in(SeckillGoodsTemplate::getTemplateId, templateIds));
	}

	@Override
	public void removeByTemplateId(String templateId) {
		this.remove(Wrappers.<SeckillGoodsTemplate>lambdaQuery()
				.eq(SeckillGoodsTemplate::getTemplateId, templateId));
	}
}
