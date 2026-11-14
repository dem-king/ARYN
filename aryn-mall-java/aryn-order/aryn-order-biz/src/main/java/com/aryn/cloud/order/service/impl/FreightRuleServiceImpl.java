package com.aryn.cloud.order.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.FreightConstants;
import com.aryn.cloud.order.api.entity.FreightRule;
import com.aryn.cloud.order.mapper.FreightRuleMapper;
import com.aryn.cloud.order.service.IFreightRuleService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 运费规则服务实现
 *
 * <p>规则读取依赖多租户拦截器自动注入 {@code tenant_id}，与 {@code delivery_area} 同口径；
 * 因此 {@code freight_rule} 必须同时登记在 boot 白名单与 cloud 的 Nacos 租户表清单，
 * 否则会跨租户串读门槛。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Service
public class FreightRuleServiceImpl extends ServiceImpl<FreightRuleMapper, FreightRule>
		implements IFreightRuleService {

	@Override
	public FreightRule match(String bizScene, String deliveryWay, String provinceCode) {
		if (StrUtil.isBlank(deliveryWay)) {
			return null;
		}
		List<FreightRule> candidates = list(Wrappers.<FreightRule>lambdaQuery()
			.eq(FreightRule::getEnabled, FreightConstants.ENABLED)
			.and(wrapper -> wrapper.eq(FreightRule::getDeliveryWay, deliveryWay)
				.or()
				.eq(FreightRule::getDeliveryWay, FreightConstants.ALL))
			.and(wrapper -> {
				if (StrUtil.isBlank(bizScene)) {
					wrapper.eq(FreightRule::getBizScene, FreightConstants.ALL);
				}
				else {
					wrapper.eq(FreightRule::getBizScene, bizScene)
						.or()
						.eq(FreightRule::getBizScene, FreightConstants.ALL);
				}
			})
			.orderByDesc(FreightRule::getPriority)
			.orderByDesc(FreightRule::getCreateTime));
		if (candidates.isEmpty()) {
			return null;
		}
		// 指定省份的规则优先于全国规则（偏远地区加价靠这一条生效），同类型再按 priority 取大
		FreightRule best = null;
		long bestScore = Long.MIN_VALUE;
		for (FreightRule rule : candidates) {
			boolean provinceSpecific = StrUtil.isNotBlank(rule.getProvinceCodes());
			if (provinceSpecific && !containsProvince(rule.getProvinceCodes(), provinceCode)) {
				continue;
			}
			long score = (provinceSpecific ? 10000L : 0L)
					+ (rule.getPriority() == null ? 0L : rule.getPriority());
			if (score > bestScore) {
				best = rule;
				bestScore = score;
			}
		}
		return best;
	}

	private boolean containsProvince(String provinceCodes, String provinceCode) {
		if (StrUtil.isBlank(provinceCode)) {
			return false;
		}
		return Arrays.stream(provinceCodes.split(","))
			.map(String::trim)
			.anyMatch(provinceCode::equals);
	}

	@Override
	public List<FreightRule> listEnabled() {
		return list(Wrappers.<FreightRule>lambdaQuery()
			.eq(FreightRule::getEnabled, FreightConstants.ENABLED)
			.orderByDesc(FreightRule::getPriority));
	}

	@Override
	public boolean saveRule(FreightRule rule) {
		validate(rule);
		ensureNameUnique(rule);
		return save(rule);
	}

	@Override
	public boolean updateRule(FreightRule rule) {
		if (StrUtil.isBlank(rule.getId())) {
			throw new ArynBusinessException("规则ID不能为空");
		}
		validate(rule);
		ensureNameUnique(rule);
		return updateById(rule);
	}

	/**
	 * 规则名称租户内唯一：唯一键在库里，这里先查一次给出可读提示，避免把 SQL 异常抛给运营。
	 */
	private void ensureNameUnique(FreightRule rule) {
		Long exists = count(Wrappers.<FreightRule>lambdaQuery()
			.eq(FreightRule::getRuleName, rule.getRuleName())
			.ne(StrUtil.isNotBlank(rule.getId()), FreightRule::getId, rule.getId()));
		if (exists != null && exists > 0) {
			throw new ArynBusinessException("规则名称已存在：" + rule.getRuleName());
		}
	}

	/**
	 * 合法性校验：场景/配送方式必须落在后端计费支持的取值内，
	 * 门槛与运费不得为负，避免配出「负运费」把订单金额打成负数。
	 */
	private void validate(FreightRule rule) {
		if (rule == null || StrUtil.isBlank(rule.getRuleName())) {
			throw new ArynBusinessException("规则名称不能为空");
		}
		if (!List.of("0", "1", "2", "3").contains(StrUtil.blankToDefault(rule.getBizScene(), "0"))) {
			throw new ArynBusinessException("购买场景不合法");
		}
		// 2 自提 / 4 到船 恒定免运费，不允许配规则，避免运营误以为配了就会收费
		if (!List.of("0", "1", "3").contains(StrUtil.blankToDefault(rule.getDeliveryWay(), "1"))) {
			throw new ArynBusinessException("配送方式不合法：仅支持普通快递与公司配送（岸上）");
		}
		if (rule.getThresholdAmount() == null || rule.getThresholdAmount().compareTo(BigDecimal.ZERO) < 0) {
			throw new ArynBusinessException("包邮门槛不能为负数");
		}
		if (rule.getBaseFreight() == null || rule.getBaseFreight().compareTo(BigDecimal.ZERO) < 0) {
			throw new ArynBusinessException("基础运费不能为负数");
		}
		// 归一化：场景/配送方式留空按「全部 / 普通快递」落库，否则空串永远匹配不上查询条件
		rule.setBizScene(StrUtil.blankToDefault(rule.getBizScene(), FreightConstants.ALL));
		rule.setDeliveryWay(StrUtil.blankToDefault(rule.getDeliveryWay(), "1"));
		if (StrUtil.isNotBlank(rule.getProvinceCodes())) {
			rule.setProvinceCodes(Arrays.stream(rule.getProvinceCodes().split(","))
				.map(String::trim)
				.filter(StrUtil::isNotBlank)
				.distinct()
				.collect(java.util.stream.Collectors.joining(",")));
			if (rule.getProvinceCodes().isEmpty()) {
				rule.setProvinceCodes(null);
			}
		}
		if (rule.getMaxFreight() != null && rule.getMaxFreight().compareTo(BigDecimal.ZERO) <= 0) {
			throw new ArynBusinessException("运费封顶必须大于 0，或留空表示不封顶");
		}
		if (rule.getPriority() == null) {
			rule.setPriority(0);
		}
		if (StrUtil.isBlank(rule.getEnabled())) {
			rule.setEnabled(FreightConstants.ENABLED);
		}
		// 门槛规则必须配基础运费，否则「不满门槛」与「满门槛」都是 0 元，规则没有意义
		if (rule.getThresholdAmount().compareTo(BigDecimal.ZERO) > 0
				&& rule.getBaseFreight().compareTo(BigDecimal.ZERO) == 0) {
			throw new ArynBusinessException("设置包邮门槛时基础运费必须大于 0");
		}
	}

}
