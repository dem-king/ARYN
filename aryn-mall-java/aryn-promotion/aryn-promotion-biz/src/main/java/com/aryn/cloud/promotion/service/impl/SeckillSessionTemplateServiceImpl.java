package com.aryn.cloud.promotion.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.SeckillGoodsDTO;
import com.aryn.cloud.promotion.api.dto.SeckillSessionTemplateDTO;
import com.aryn.cloud.promotion.api.entity.SeckillGoodsTemplate;
import com.aryn.cloud.promotion.api.entity.SeckillSessionTemplate;
import com.aryn.cloud.promotion.api.vo.SeckillGoodsVO;
import com.aryn.cloud.promotion.api.vo.SeckillSessionTemplateVO;
import com.aryn.cloud.promotion.mapper.SeckillSessionTemplateMapper;
import com.aryn.cloud.promotion.service.ISeckillGoodsTemplateService;
import com.aryn.cloud.promotion.service.ISeckillSessionTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillSessionTemplateServiceImpl
		extends ServiceImpl<SeckillSessionTemplateMapper, SeckillSessionTemplate>
		implements ISeckillSessionTemplateService {

	private static final String ALL_WEEKDAYS = "1,2,3,4,5,6,7";

	private final ISeckillGoodsTemplateService seckillGoodsTemplateService;

	@Override
	public List<SeckillSessionTemplate> listByActivityId(String activityId) {
		return this.list(Wrappers.<SeckillSessionTemplate>lambdaQuery()
				.eq(SeckillSessionTemplate::getActivityId, activityId)
				.orderByAsc(SeckillSessionTemplate::getStartTime));
	}

	@Override
	public List<SeckillSessionTemplateVO> listVoByActivityId(String activityId) {
		List<SeckillSessionTemplate> templates = listByActivityId(activityId);
		if (CollectionUtils.isEmpty(templates)) {
			return List.of();
		}
		List<String> templateIds = templates.stream().map(SeckillSessionTemplate::getId).toList();
		Map<String, List<SeckillGoodsTemplate>> goodsByTemplate = seckillGoodsTemplateService
				.listByTemplateIds(templateIds).stream()
				.collect(Collectors.groupingBy(SeckillGoodsTemplate::getTemplateId));
		List<SeckillSessionTemplateVO> result = new ArrayList<>();
		for (SeckillSessionTemplate template : templates) {
			SeckillSessionTemplateVO vo = new SeckillSessionTemplateVO();
			BeanUtils.copyProperties(template, vo);
			List<SeckillGoodsVO> goodsList = goodsByTemplate
					.getOrDefault(template.getId(), List.of()).stream()
					.map(goods -> {
						SeckillGoodsVO goodsVO = new SeckillGoodsVO();
						BeanUtils.copyProperties(goods, goodsVO);
						return goodsVO;
					})
					.toList();
			vo.setGoodsList(goodsList);
			result.add(vo);
		}
		return result;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void saveTemplates(String activityId, List<SeckillSessionTemplateDTO> templates) {
		removeByActivityId(activityId);
		if (CollectionUtils.isEmpty(templates)) {
			return;
		}
		validateTemplates(templates);
		for (SeckillSessionTemplateDTO dto : templates) {
			SeckillSessionTemplate template = new SeckillSessionTemplate();
			template.setActivityId(activityId);
			template.setSessionName(dto.getSessionName());
			template.setStartTime(dto.getStartTime());
			template.setEndTime(dto.getEndTime());
			template.setWeekdays(normalizeWeekdays(dto.getWeekdays()));
			template.setStatus(0);
			this.save(template);
			List<SeckillGoodsTemplate> goodsTemplates = new ArrayList<>();
			for (SeckillGoodsDTO goodsDTO : dto.getGoodsList()) {
				SeckillGoodsTemplate goods = new SeckillGoodsTemplate();
				goods.setActivityId(activityId);
				goods.setTemplateId(template.getId());
				goods.setSpuId(goodsDTO.getSpuId());
				goods.setSkuId(goodsDTO.getSkuId());
				goods.setSeckillPrice(goodsDTO.getSeckillPrice());
				goods.setSeckillStock(goodsDTO.getSeckillStock());
				goods.setLimitPerUser(goodsDTO.getLimitPerUser() == null ? 1 : goodsDTO.getLimitPerUser());
				goodsTemplates.add(goods);
			}
			seckillGoodsTemplateService.saveBatch(goodsTemplates);
		}
		log.info("秒杀排期模板保存完成, activityId={}, 模板数={}", activityId, templates.size());
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void removeByActivityId(String activityId) {
		List<SeckillSessionTemplate> oldTemplates = listByActivityId(activityId);
		for (SeckillSessionTemplate template : oldTemplates) {
			seckillGoodsTemplateService.removeByTemplateId(template.getId());
		}
		if (!oldTemplates.isEmpty()) {
			this.removeByIds(oldTemplates.stream().map(SeckillSessionTemplate::getId).toList());
		}
	}

	private void validateTemplates(List<SeckillSessionTemplateDTO> templates) {
		for (int i = 0; i < templates.size(); i++) {
			SeckillSessionTemplateDTO current = templates.get(i);
			if (current.getStartTime() == null || current.getEndTime() == null
					|| !current.getStartTime().isBefore(current.getEndTime())) {
				throw new ArynBusinessException("第 " + (i + 1) + " 个场次的结束时间必须晚于开始时间");
			}
			if (CollectionUtils.isEmpty(current.getGoodsList())) {
				throw new ArynBusinessException("第 " + (i + 1) + " 个场次至少添加一个商品");
			}
			Set<String> skuIds = new HashSet<>();
			for (SeckillGoodsDTO goods : current.getGoodsList()) {
				if (!skuIds.add(goods.getSkuId())) {
					throw new ArynBusinessException("第 " + (i + 1) + " 个场次存在重复商品");
				}
			}
			Set<Integer> currentDays = parseWeekdays(current.getWeekdays());
			for (int j = i + 1; j < templates.size(); j++) {
				SeckillSessionTemplateDTO other = templates.get(j);
				Set<Integer> otherDays = parseWeekdays(other.getWeekdays());
				if (!intersect(currentDays, otherDays)) {
					continue;
				}
				if (overlaps(current.getStartTime(), current.getEndTime(),
						other.getStartTime(), other.getEndTime())) {
					throw new ArynBusinessException("第 " + (i + 1) + " 个场次与第 " + (j + 1)
							+ " 个场次时间重叠，请调整时段");
				}
			}
		}
	}

	private boolean overlaps(LocalTime start1, LocalTime end1, LocalTime start2, LocalTime end2) {
		return start1.isBefore(end2) && start2.isBefore(end1);
	}

	private boolean intersect(Set<Integer> left, Set<Integer> right) {
		return left.stream().anyMatch(right::contains);
	}

	/** 空/全选统一存全量 7 天，生成场次时无需再判空 */
	private String normalizeWeekdays(String weekdays) {
		Set<Integer> days = parseWeekdays(weekdays);
		if (days.isEmpty() || days.size() == 7) {
			return ALL_WEEKDAYS;
		}
		return days.stream().sorted().map(String::valueOf).collect(Collectors.joining(","));
	}

	private Set<Integer> parseWeekdays(String weekdays) {
		if (!StringUtils.hasText(weekdays)) {
			return Set.of(1, 2, 3, 4, 5, 6, 7);
		}
		Set<Integer> days = new HashSet<>();
		for (String part : weekdays.split(",")) {
			try {
				int day = Integer.parseInt(part.trim());
				if (day >= 1 && day <= 7) {
					days.add(day);
				}
			}
			catch (NumberFormatException ignored) {
				// 非法值忽略，避免脏配置阻断保存
			}
		}
		// 全选与空等价，统一按每天处理
		if (days.isEmpty()) {
			return Set.of(1, 2, 3, 4, 5, 6, 7);
		}
		return days;
	}
}
