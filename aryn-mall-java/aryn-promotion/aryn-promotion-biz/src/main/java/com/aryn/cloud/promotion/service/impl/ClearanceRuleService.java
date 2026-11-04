package com.aryn.cloud.promotion.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.ClearanceRule;
import com.aryn.cloud.promotion.mapper.ClearanceRuleMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 出清时间分层规则：距到期越近，在基础出清价上再打折。
 *
 * <p>同一时刻可能命中多条规则，只应用最深折扣（最小 discountRate）一次，不连乘；
 * 算价在系统活动同步任务中完成，下单链路只读最终价，避免 preview/reserve 时间基准不一致。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Service
@RequiredArgsConstructor
public class ClearanceRuleService {

	private final ClearanceRuleMapper clearanceRuleMapper;

	public List<ClearanceRule> list(String tenantId) {
		return clearanceRuleMapper.selectList(Wrappers.<ClearanceRule>lambdaQuery()
			.eq(ClearanceRule::getTenantId, tenantId)
			.orderByAsc(ClearanceRule::getSortOrder)
			.orderByDesc(ClearanceRule::getHoursBeforeExpire));
	}

	public List<ClearanceRule> listEnabled(String tenantId) {
		return clearanceRuleMapper.selectList(Wrappers.<ClearanceRule>lambdaQuery()
			.eq(ClearanceRule::getTenantId, tenantId)
			.eq(ClearanceRule::getEnabled, "1")
			.orderByAsc(ClearanceRule::getHoursBeforeExpire));
	}

	@Transactional(rollbackFor = Exception.class)
	public ClearanceRule save(String tenantId, ClearanceRule rule) {
		validate(rule);
		rule.setTenantId(tenantId);
		rule.setDelFlag("0");
		rule.setCreateTime(LocalDateTime.now());
		if (!StringUtils.hasText(rule.getEnabled())) {
			rule.setEnabled("1");
		}
		if (clearanceRuleMapper.insert(rule) <= 0) {
			throw new ArynBusinessException("出清规则保存失败");
		}
		return rule;
	}

	@Transactional(rollbackFor = Exception.class)
	public ClearanceRule update(String tenantId, ClearanceRule rule) {
		if (!StringUtils.hasText(rule.getId())) {
			throw new ArynBusinessException("规则标识不能为空");
		}
		ClearanceRule exists = clearanceRuleMapper.selectOne(Wrappers.<ClearanceRule>lambdaQuery()
			.eq(ClearanceRule::getTenantId, tenantId)
			.eq(ClearanceRule::getId, rule.getId()));
		if (exists == null) {
			throw new ArynBusinessException("规则不存在或无权操作");
		}
		validate(rule);
		rule.setTenantId(tenantId);
		clearanceRuleMapper.updateById(rule);
		return rule;
	}

	@Transactional(rollbackFor = Exception.class)
	public void delete(String tenantId, String id) {
		ClearanceRule exists = clearanceRuleMapper.selectOne(Wrappers.<ClearanceRule>lambdaQuery()
			.eq(ClearanceRule::getTenantId, tenantId)
			.eq(ClearanceRule::getId, id));
		if (exists == null) {
			throw new ArynBusinessException("规则不存在或无权操作");
		}
		clearanceRuleMapper.deleteById(id);
	}

	/**
	 * 当前生效的分层折扣率：剩余小时数 ≤ hoursBeforeExpire 的规则中取最小折扣率；
	 * 无到期时间/无命中返回 null。
	 */
	public BigDecimal resolveTierRate(List<ClearanceRule> rules, LocalDateTime expireTime, LocalDateTime now) {
		if (expireTime == null || rules == null || rules.isEmpty()) {
			return null;
		}
		long remainingHours = Duration.between(now, expireTime).toHours();
		if (remainingHours < 0) {
			return null;
		}
		BigDecimal best = null;
		for (ClearanceRule rule : rules) {
			if (rule == null || rule.getDiscountRate() == null || rule.getHoursBeforeExpire() == null
					|| !"1".equals(rule.getEnabled())) {
				continue;
			}
			if (remainingHours <= rule.getHoursBeforeExpire()
					&& (best == null || rule.getDiscountRate().compareTo(best) < 0)) {
				best = rule.getDiscountRate();
			}
		}
		return best;
	}

	private void validate(ClearanceRule rule) {
		if (rule == null || !StringUtils.hasText(rule.getRuleName())) {
			throw new ArynBusinessException("规则名称不能为空");
		}
		if (rule.getHoursBeforeExpire() == null || rule.getHoursBeforeExpire() <= 0) {
			throw new ArynBusinessException("距到期小时数必须大于 0");
		}
		BigDecimal rate = rule.getDiscountRate();
		if (rate == null || rate.signum() <= 0 || rate.compareTo(BigDecimal.ONE) >= 0) {
			throw new ArynBusinessException("分层折扣率必须在 (0, 1) 区间内，如 0.8 表示 8 折");
		}
	}

}
