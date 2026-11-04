package com.aryn.cloud.product.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.product.api.entity.GoodsPriceHistory;
import com.aryn.cloud.product.mapper.GoodsPriceHistoryMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 商品价格变更历史：只记录有效变化，查询按租户隔离。
 *
 * <p>「先涨后降」判定只看 sales 价的近期上调记录；出清/会员规则变更同样落历史，
 * 供运营追溯专区价格来源。操作人从登录态写入（后台任务无登录态时为空）。
 *
 * @author aryn
 * @since 2026/11/03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsPriceHistoryService {

	private static final int MAX_QUERY_LIMIT = 500;

	private final GoodsPriceHistoryMapper goodsPriceHistoryMapper;

	public void recordIfChanged(String spuId, String skuId, String priceType, BigDecimal oldValue,
			BigDecimal newValue, String source, String reason) {
		if (!StringUtils.hasText(spuId) || !StringUtils.hasText(priceType) || sameValue(oldValue, newValue)) {
			return;
		}
		GoodsPriceHistory history = new GoodsPriceHistory();
		history.setTenantId(ArynTenantContextHolder.getTenantId());
		history.setSpuId(spuId);
		history.setSkuId(skuId);
		history.setPriceType(priceType);
		history.setOldValue(oldValue);
		history.setNewValue(newValue);
		history.setChangeSource(source);
		history.setChangeReason(reason);
		history.setOperator(currentOperator());
		history.setCreateTime(LocalDateTime.now());
		goodsPriceHistoryMapper.insert(history);
	}

	/**
	 * 当前操作人展示名（昵称优先，回退用户名）；无登录态/异常时返回 null。
	 */
	public String currentOperator() {
		try {
			ArynUser user = SecurityUtils.getUser();
			if (user == null) {
				return null;
			}
			return StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
		}
		catch (Exception ex) {
			log.debug("读取当前登录用户失败，价格历史操作人置空", ex);
			return null;
		}
	}

	public List<GoodsPriceHistory> listBySpu(String spuId, int limit) {
		int size = limit <= 0 ? 100 : Math.min(limit, MAX_QUERY_LIMIT);
		return goodsPriceHistoryMapper.selectList(Wrappers.<GoodsPriceHistory>lambdaQuery()
			.eq(GoodsPriceHistory::getSpuId, spuId)
			.orderByDesc(GoodsPriceHistory::getCreateTime)
			.last("LIMIT " + size));
	}

	/**
	 * 近 N 天是否有售价上调记录：用于出清打标时的「先涨后降」提示。
	 */
	public boolean hasRecentSalesIncrease(String spuId, int days) {
		if (!StringUtils.hasText(spuId)) {
			return false;
		}
		long count = goodsPriceHistoryMapper.selectCount(Wrappers.<GoodsPriceHistory>lambdaQuery()
			.eq(GoodsPriceHistory::getSpuId, spuId)
			.eq(GoodsPriceHistory::getPriceType, "sales")
			.ge(GoodsPriceHistory::getCreateTime, LocalDateTime.now().minusDays(Math.max(days, 1)))
			.apply("new_value > old_value"));
		return count > 0;
	}

	/**
	 * 批量判定近 N 天售价上调过的商品（一次查询，供批量预检/执行使用）。
	 */
	public Set<String> recentSalesIncreaseSpuIds(Collection<String> spuIds, int days) {
		if (CollectionUtils.isEmpty(spuIds)) {
			return Set.of();
		}
		List<GoodsPriceHistory> histories = goodsPriceHistoryMapper.selectList(
			Wrappers.<GoodsPriceHistory>lambdaQuery()
				.in(GoodsPriceHistory::getSpuId, spuIds)
				.eq(GoodsPriceHistory::getPriceType, "sales")
				.ge(GoodsPriceHistory::getCreateTime, LocalDateTime.now().minusDays(Math.max(days, 1)))
				.apply("new_value > old_value"));
		return histories.stream()
			.map(GoodsPriceHistory::getSpuId)
			.filter(StringUtils::hasText)
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private boolean sameValue(BigDecimal left, BigDecimal right) {
		if (left == null && right == null) {
			return true;
		}
		if (left == null || right == null) {
			return false;
		}
		return Objects.equals(left.stripTrailingZeros(), right.stripTrailingZeros());
	}

}
