package com.aryn.cloud.promotion.service.impl;

import cn.hutool.core.util.IdUtil;
import com.aryn.cloud.promotion.api.dto.PolicyPriceQueryDTO;
import com.aryn.cloud.promotion.api.entity.PricePolicy;
import com.aryn.cloud.promotion.api.entity.PricePolicyItem;
import com.aryn.cloud.promotion.api.vo.PolicyDryRunVO;
import com.aryn.cloud.promotion.api.vo.PolicyPriceVO;
import com.aryn.cloud.promotion.mapper.PricePolicyItemMapper;
import com.aryn.cloud.promotion.mapper.PricePolicyMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 价格政策（协议价）服务：管理端 CRUD/发布 + C 端批量查价。
 *
 * <p>计价侧的最高优先级价格层由营销引擎 {@code applyPolicyPrices} 消费；
 * 本服务只管配置与展示查询，两者共用同一套命中口径
 * （apply_type 匹配 + priority 取大 + 同级取低价 + 倒挂不落价）。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PricePolicyService {

	private final PricePolicyMapper pricePolicyMapper;

	private final PricePolicyItemMapper pricePolicyItemMapper;

	/**
	 * 商品售价远程接口：折扣率模式折算与划线基准需要实时售价。
	 * 非 final 字段 + @DubboReference（构造器注入远程接口曾在 boot 启动失败，与引擎同款写法）。
	 */
	@DubboReference
	private com.aryn.cloud.product.api.remote.RemoteGoodsSkuService remoteGoodsSkuService;

	/** 企业会员远程服务：客群标签（apply_type=3）圈定需要用户标签集合；失败按无标签处理（fail-closed）。 */
	@DubboReference
	private com.aryn.cloud.user.api.remote.RemoteEnterpriseUserService remoteEnterpriseUserService;

	/** 政策列表（管理端，含全部状态，del_flag 过滤由逻辑删除插件处理）。 */
	public List<PricePolicy> list(String tenantId, String status, String keyword) {
		return pricePolicyMapper.selectList(Wrappers.lambdaQuery(PricePolicy.class)
			.eq(PricePolicy::getTenantId, tenantId)
			.eq(StringUtils.hasText(status), PricePolicy::getStatus, status)
			.like(StringUtils.hasText(keyword), PricePolicy::getPolicyName, keyword)
			.orderByDesc(PricePolicy::getPriority)
			.orderByDesc(PricePolicy::getCreateTime));
	}

	public PricePolicy get(String tenantId, String id) {
		PricePolicy policy = pricePolicyMapper.selectOne(Wrappers.lambdaQuery(PricePolicy.class)
			.eq(PricePolicy::getTenantId, tenantId)
			.eq(PricePolicy::getId, id));
		return policy;
	}

	/** 政策 SKU 明细（管理端编辑回显）。 */
	public List<PricePolicyItem> listItems(String tenantId, String policyId) {
		return pricePolicyItemMapper.selectList(Wrappers.lambdaQuery(PricePolicyItem.class)
			.eq(PricePolicyItem::getTenantId, tenantId)
			.eq(PricePolicyItem::getPolicyId, policyId)
			.orderByAsc(PricePolicyItem::getSkuId));
	}

	/** 新建政策（草稿或直接生效），明细按唯一键 upsert。 */
	@Transactional(rollbackFor = Exception.class)
	public PricePolicy save(String tenantId, PricePolicy policy, List<PricePolicyItem> items) {
		policy.setId(null);
		policy.setTenantId(tenantId);
		validatePolicy(policy);
		pricePolicyMapper.insert(policy);
		upsertItems(tenantId, policy.getId(), items);
		return policy;
	}

	/** 修改政策基础信息与明细（按唯一键 upsert 复用行，payload 外的存量明细逻辑删除）。 */
	@Transactional(rollbackFor = Exception.class)
	public PricePolicy update(String tenantId, String id, PricePolicy policy, List<PricePolicyItem> items) {
		PricePolicy existing = get(tenantId, id);
		if (existing == null) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("价格政策不存在");
		}
		if (PricePolicy.STATUS_ACTIVE.equals(existing.getStatus())) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException(
				"生效中的政策不可修改，请先停用（合同价稳定性优先）");
		}
		policy.setId(id);
		policy.setTenantId(tenantId);
		validatePolicy(policy);
		pricePolicyMapper.updateById(policy);
		upsertItems(tenantId, id, items);
		return get(tenantId, id);
	}

	/**
	 * 明细按唯一键 (tenant, policy, sku) upsert：新增插入、已有行复活（del_flag=0）并覆盖价格，
	 * payload 外的存量明细逻辑删除（复用行，不物理删除，避免唯一键撞车）。
	 */
	private void upsertItems(String tenantId, String policyId, List<PricePolicyItem> items) {
		List<PricePolicyItem> incoming = items == null ? List.of() : items.stream()
			.filter(item -> StringUtils.hasText(item.getSkuId()))
			.toList();
		Set<String> incomingSkuIds = incoming.stream().map(PricePolicyItem::getSkuId).collect(Collectors.toSet());
		List<PricePolicyItem> existing = listItems(tenantId, policyId);
		for (PricePolicyItem item : existing) {
			if (!incomingSkuIds.contains(item.getSkuId())) {
				pricePolicyItemMapper.deleteById(item.getId());
			}
		}
		for (PricePolicyItem item : incoming) {
			PricePolicyItem row = new PricePolicyItem();
			row.setId(IdUtil.getSnowflakeNextIdStr());
			row.setPolicyId(policyId);
			row.setSpuId(StringUtils.hasText(item.getSpuId()) ? item.getSpuId() : "");
			row.setSkuId(item.getSkuId());
			row.setPolicyPrice(item.getPolicyPrice());
			row.setPolicyRate(item.getPolicyRate());
			row.setMinQty(item.getMinQty() == null || item.getMinQty() < 1 ? 1 : item.getMinQty());
			row.setTenantId(tenantId);
			row.setDelFlag("0");
			// 唯一键 upsert：历史行（含已逻辑删除）复活并覆盖价格，禁止物理删除
			pricePolicyItemMapper.upsert(row);
		}
	}

	/**
	 * 发布/停用：status 在 0草稿/2停用 ↔ 1生效 间切换。
	 * 发布校验：明细非空、时间窗合法；生效中的政策不可修改基础信息（合同稳定性）。
	 */
	@Transactional(rollbackFor = Exception.class)
	public PricePolicy changeStatus(String tenantId, String id, String targetStatus) {
		PricePolicy existing = get(tenantId, id);
		if (existing == null) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("价格政策不存在");
		}
		if (!PricePolicy.STATUS_ACTIVE.equals(targetStatus)
				&& !PricePolicy.STATUS_DISABLED.equals(targetStatus)
				&& !PricePolicy.STATUS_DRAFT.equals(targetStatus)) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("非法的目标状态");
		}
		if (PricePolicy.STATUS_ACTIVE.equals(targetStatus)) {
			if (CollectionUtils.isEmpty(listItems(tenantId, id))) {
				throw new com.aryn.cloud.common.security.handler.ArynBusinessException("发布失败：政策没有配置任何 SKU 明细");
			}
			LocalDateTime now = LocalDateTime.now();
			if (existing.getStartTime() == null || existing.getEndTime() == null
					|| existing.getEndTime().isBefore(existing.getStartTime()) || existing.getEndTime().isBefore(now)) {
				throw new com.aryn.cloud.common.security.handler.ArynBusinessException("发布失败：生效时间窗非法");
			}
		}
		existing.setStatus(targetStatus);
		pricePolicyMapper.updateById(existing);
		return existing;
	}

	/** 逻辑删除政策及其明细（明细复用行，禁止物理删除）。 */
	@Transactional(rollbackFor = Exception.class)
	public void delete(String tenantId, String id) {
		PricePolicy existing = get(tenantId, id);
		if (existing == null) {
			return;
		}
		pricePolicyMapper.deleteById(id);
		List<PricePolicyItem> items = listItems(tenantId, id);
		for (PricePolicyItem item : items) {
			pricePolicyItemMapper.deleteById(item.getId());
		}
	}

	/**
	 * C 端批量查价：按登录态 + 会话上下文（主体/船舶）解析命中政策，返回各 SKU 的协议价。
	 *
	 * <p>展示口径与成交口径（引擎 applyPolicyPrices）一致：priority 取大、同级取低价、
	 * 倒挂不落价；未登录/未命中返回空列表（前端按无协议价渲染）。
	 */
	public List<PolicyPriceVO> policyPrices(String tenantId, String userId, PolicyPriceQueryDTO query) {
		if (query == null || (CollectionUtils.isEmpty(query.getSkuIds()) && CollectionUtils.isEmpty(query.getSpuIds()))) {
			return List.of();
		}
		List<PricePolicy> policies = effectivePolicies(tenantId, userId, query.getBuyerOrgId(), query.getVesselId());
		if (policies.isEmpty()) {
			return List.of();
		}
		Map<String, PricePolicy> policyById = policies.stream()
			.collect(Collectors.toMap(PricePolicy::getId, policy -> policy, (left, right) -> left));
		List<PricePolicyItem> items = pricePolicyItemMapper.selectList(Wrappers.lambdaQuery(PricePolicyItem.class)
			.eq(PricePolicyItem::getTenantId, tenantId)
			.in(!CollectionUtils.isEmpty(query.getSkuIds()), PricePolicyItem::getSkuId, query.getSkuIds())
			.in(!CollectionUtils.isEmpty(query.getSpuIds()) && CollectionUtils.isEmpty(query.getSkuIds()),
					PricePolicyItem::getSpuId, query.getSpuIds())
			.in(PricePolicyItem::getPolicyId, policyById.keySet()));
		if (items.isEmpty()) {
			return List.of();
		}
		Map<String, BigDecimal> salesPriceBySku = salesPrices(items.stream()
			.map(PricePolicyItem::getSkuId).distinct().toList());
		Map<String, PricePolicyItem> bestBySku = new LinkedHashMap<>();
		Map<String, PricePolicy> bestPolicyBySku = new LinkedHashMap<>();
		Map<String, BigDecimal> bestPriceBySku = new LinkedHashMap<>();
		for (PricePolicyItem item : items) {
			PricePolicy policy = policyById.get(item.getPolicyId());
			BigDecimal salesPrice = salesPriceBySku.get(item.getSkuId());
			if (policy == null || salesPrice == null) {
				continue;
			}
			BigDecimal price = resolvePolicyPrice(item, policy.getPriceMode(), salesPrice);
			if (price == null || price.compareTo(salesPrice) >= 0) {
				continue;
			}
			int priority = policy.getPriority() == null ? 0 : policy.getPriority();
			PricePolicy bestPolicy = bestPolicyBySku.get(item.getSkuId());
			if (bestPolicy == null) {
				bestBySku.put(item.getSkuId(), item);
				bestPolicyBySku.put(item.getSkuId(), policy);
				bestPriceBySku.put(item.getSkuId(), price);
				continue;
			}
			int bestPriority = bestPolicy.getPriority() == null ? 0 : bestPolicy.getPriority();
			if (priority > bestPriority || (priority == bestPriority
					&& price.compareTo(bestPriceBySku.get(item.getSkuId())) < 0)) {
				bestBySku.put(item.getSkuId(), item);
				bestPolicyBySku.put(item.getSkuId(), policy);
				bestPriceBySku.put(item.getSkuId(), price);
			}
		}
		List<PolicyPriceVO> result = new ArrayList<>(bestBySku.size());
		for (Map.Entry<String, PricePolicyItem> entry : bestBySku.entrySet()) {
			String skuId = entry.getKey();
			PolicyPriceVO vo = new PolicyPriceVO();
			vo.setSkuId(skuId);
			vo.setSpuId(entry.getValue().getSpuId());
			vo.setPolicyId(bestPolicyBySku.get(skuId).getId());
			vo.setPolicyName(bestPolicyBySku.get(skuId).getPolicyName());
			vo.setPolicyPrice(bestPriceBySku.get(skuId));
			vo.setOriginalPrice(salesPriceBySku.get(skuId));
			vo.setMinQty(entry.getValue().getMinQty());
			result.add(vo);
		}
		return result;
	}

	/** 生效中的政策（时间窗覆盖当前）按上下文过滤；标签集合失败按空（fail-closed）。 */
	private List<PricePolicy> effectivePolicies(String tenantId, String userId, String buyerOrgId, String vesselId) {
		LocalDateTime now = LocalDateTime.now();
		List<PricePolicy> policies = pricePolicyMapper.selectList(Wrappers.lambdaQuery(PricePolicy.class)
			.eq(PricePolicy::getTenantId, tenantId)
			.eq(PricePolicy::getStatus, PricePolicy.STATUS_ACTIVE)
			.le(PricePolicy::getStartTime, now)
			.ge(PricePolicy::getEndTime, now));
		if (policies.isEmpty()) {
			return List.of();
		}
		Set<String> userTagIds = null;
		List<PricePolicy> matched = new ArrayList<>(policies.size());
		for (PricePolicy policy : policies) {
			String applyType = policy.getApplyType();
			if (PricePolicy.APPLY_BUYER_ORG.equals(applyType)) {
				if (containsValue(policy.getApplyValue(), buyerOrgId)) {
					matched.add(policy);
				}
			}
			else if (PricePolicy.APPLY_VESSEL.equals(applyType)) {
				if (containsValue(policy.getApplyValue(), vesselId)) {
					matched.add(policy);
				}
			}
			else if (PricePolicy.APPLY_TAG.equals(applyType)) {
				if (userTagIds == null) {
					userTagIds = fetchUserTagIds(tenantId, userId);
				}
				if (StringUtils.hasText(policy.getApplyValue()) && !userTagIds.isEmpty()
						&& Arrays.asList(policy.getApplyValue().split(",")).stream().anyMatch(userTagIds::contains)) {
					matched.add(policy);
				}
			}
		}
		return matched;
	}

	private Set<String> fetchUserTagIds(String tenantId, String userId) {
		if (!StringUtils.hasText(userId)) {
			return Set.of();
		}
		try {
			List<String> tagIds = remoteEnterpriseUserService.getUserTagIds(tenantId, userId);
			return tagIds == null ? Set.of() : Set.copyOf(tagIds);
		}
		catch (Exception ex) {
			log.warn("协议价客群标签查询失败，按无标签处理 tenant={} user={}: {}", tenantId, userId, ex.getMessage());
			return Set.of();
		}
	}

	private boolean containsValue(String applyValue, String value) {
		return StringUtils.hasText(applyValue) && StringUtils.hasText(value)
				&& Arrays.asList(applyValue.split(",")).contains(value);
	}

	private BigDecimal resolvePolicyPrice(PricePolicyItem item, String priceMode, BigDecimal salesPrice) {
		if (PricePolicy.MODE_FIXED_PRICE.equals(priceMode) && item.getPolicyPrice() != null) {
			return item.getPolicyPrice();
		}
		if (PricePolicy.MODE_RATE.equals(priceMode) && item.getPolicyRate() != null
				&& item.getPolicyRate().signum() > 0) {
			return salesPrice.multiply(item.getPolicyRate()).setScale(2, RoundingMode.DOWN);
		}
		return null;
	}

	/** SKU 实时售价（划线基准与折扣率折算）；查询失败按空处理（展示侧静默，成交侧走引擎）。 */
	private Map<String, BigDecimal> salesPrices(List<String> skuIds) {
		if (skuIds.isEmpty()) {
			return Map.of();
		}
		try {
			return remoteGoodsSkuService.getBySkuIds(skuIds).stream()
				.filter(sku -> sku.getSalesPrice() != null)
				.collect(Collectors.toMap(com.aryn.cloud.product.api.entity.GoodsSku::getId,
						com.aryn.cloud.product.api.entity.GoodsSku::getSalesPrice, (left, right) -> left));
		}
		catch (Exception ex) {
			log.warn("协议价展示查询 SKU 售价失败，按无协议价返回: {}", ex.getMessage());
			return Map.of();
		}
	}

	/**
	 * 发布前 dry-run 预检（2026-11-15）：逐 SKU 检索同 SKU 冲突与倒挂风险。
	 *
	 * <p>口径与引擎成交判定一致（priority 取大、同级取低价、倒挂不落价），
	 * 预检只解释「按当前配置哪个政策会赢」，不阻断发布；成本倒挂仅提示，
	 * 运营自行决策。远程售价失败时该 SKU 标记 INVERTED_SALES 不可判（降级提示）。
	 */
	public PolicyDryRunVO dryRun(String tenantId, String id) {
		PricePolicy policy = get(tenantId, id);
		if (policy == null) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("价格政策不存在");
		}
		PolicyDryRunVO result = new PolicyDryRunVO();
		result.setPolicyId(policy.getId());
		result.setPolicyName(policy.getPolicyName());
		List<PricePolicyItem> ownItems = listItems(tenantId, id);
		if (ownItems.isEmpty()) {
			return result;
		}
		List<String> skuIds = ownItems.stream().map(PricePolicyItem::getSkuId).distinct().toList();
		Map<String, com.aryn.cloud.product.api.entity.GoodsSku> skuById = fetchSkus(skuIds);
		// 其他生效中的政策（排除自身）：同 SKU 冲突检索
		List<PricePolicy> otherPolicies = pricePolicyMapper.selectList(
				Wrappers.lambdaQuery(PricePolicy.class)
					.eq(PricePolicy::getTenantId, tenantId)
					.eq(PricePolicy::getStatus, PricePolicy.STATUS_ACTIVE)
					.le(PricePolicy::getStartTime, LocalDateTime.now())
					.ge(PricePolicy::getEndTime, LocalDateTime.now())
					.ne(PricePolicy::getId, id));
		Map<String, List<PricePolicyItem>> otherItemsBySku = otherPolicies.isEmpty() ? Map.of()
				: pricePolicyItemMapper.selectList(Wrappers.lambdaQuery(PricePolicyItem.class)
					.eq(PricePolicyItem::getTenantId, tenantId)
					.in(PricePolicyItem::getPolicyId, otherPolicies.stream().map(PricePolicy::getId).toList()))
					.stream()
					.collect(java.util.stream.Collectors.groupingBy(PricePolicyItem::getSkuId));
		Map<String, PricePolicy> otherPolicyById = otherPolicies.stream()
			.collect(java.util.stream.Collectors.toMap(PricePolicy::getId, p -> p, (l, r) -> l));
		int ownPriority = policy.getPriority() == null ? 0 : policy.getPriority();
		for (PricePolicyItem item : ownItems) {
			PolicyDryRunVO.Item row = new PolicyDryRunVO.Item();
			row.setSkuId(item.getSkuId());
			com.aryn.cloud.product.api.entity.GoodsSku sku = skuById.get(item.getSkuId());
			if (sku == null || sku.getSalesPrice() == null) {
				// 售价不可得（远程失败/商品下架）：预检无法判定，按倒挂提示保守处理
				row.setVerdict("INVERTED_SALES");
				result.getItems().add(row);
				continue;
			}
			row.setSpuName(sku.getGoodsSpu() != null ? sku.getGoodsSpu().getName() : "");
			row.setSalesPrice(sku.getSalesPrice());
			row.setCostPrice(sku.getCostPrice());
			BigDecimal price = resolvePolicyPrice(item, policy.getPriceMode(), sku.getSalesPrice());
			if (price == null) {
				row.setVerdict("INVERTED_SALES");
				result.getItems().add(row);
				continue;
			}
			row.setPolicyPrice(price);
			List<String> verdicts = new ArrayList<>();
			if (price.compareTo(sku.getSalesPrice()) >= 0) {
				// 倒挂：引擎不落价，公开价格层继续生效
				verdicts.add("INVERTED_SALES");
			}
			if (sku.getCostPrice() != null && sku.getCostPrice().signum() > 0
					&& price.compareTo(sku.getCostPrice()) < 0) {
				verdicts.add("COST_RISK");
			}
			String counterparty = null;
			for (PricePolicyItem otherItem : otherItemsBySku.getOrDefault(item.getSkuId(), List.of())) {
				PricePolicy other = otherPolicyById.get(otherItem.getPolicyId());
				if (other == null) {
					continue;
				}
				int otherPriority = other.getPriority() == null ? 0 : other.getPriority();
				BigDecimal otherPrice = resolvePolicyPrice(otherItem, other.getPriceMode(), sku.getSalesPrice());
				if (otherPrice == null) {
					continue;
				}
				boolean otherWins = otherPriority > ownPriority
						|| (otherPriority == ownPriority && otherPrice.compareTo(price) < 0);
				if (otherWins) {
					verdicts.add("SHADOWED");
					counterparty = other.getPolicyName();
					break;
				}
				if (ownPriority > otherPriority
						|| (ownPriority == otherPriority && price.compareTo(otherPrice) < 0)) {
					verdicts.add("SHADOWS_OTHER");
					counterparty = other.getPolicyName();
					break;
				}
			}
			if (verdicts.isEmpty()) {
				verdicts.add("OK");
			}
			row.setVerdict(String.join(",", verdicts));
			row.setCounterpartyPolicyName(counterparty);
			result.getItems().add(row);
		}
		return result;
	}

	/** SKU 展示信息批量查询（预检用：售价/成本/名称）；失败返回空 Map（逐 SKU 降级提示）。 */
	private Map<String, com.aryn.cloud.product.api.entity.GoodsSku> fetchSkus(List<String> skuIds) {
		if (skuIds.isEmpty()) {
			return Map.of();
		}
		try {
			return remoteGoodsSkuService.getBySkuIds(skuIds).stream()
				.collect(java.util.stream.Collectors.toMap(
						com.aryn.cloud.product.api.entity.GoodsSku::getId, sku -> sku, (l, r) -> l));
		}
		catch (Exception ex) {
			log.warn("价格政策 dry-run SKU 信息查询失败，按不可判定处理: {}", ex.getMessage());
			return Map.of();
		}
	}

	private void validatePolicy(PricePolicy policy) {
		if (!StringUtils.hasText(policy.getPolicyName())) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("政策名称不能为空");
		}
		if (!PricePolicy.APPLY_BUYER_ORG.equals(policy.getApplyType())
				&& !PricePolicy.APPLY_VESSEL.equals(policy.getApplyType())
				&& !PricePolicy.APPLY_TAG.equals(policy.getApplyType())) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("非法的适用对象类型");
		}
		if (!StringUtils.hasText(policy.getApplyValue())) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("适用对象不能为空");
		}
		if (!PricePolicy.MODE_FIXED_PRICE.equals(policy.getPriceMode())
				&& !PricePolicy.MODE_RATE.equals(policy.getPriceMode())) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("非法的价格模式");
		}
		if (policy.getStartTime() == null || policy.getEndTime() == null
				|| policy.getEndTime().isBefore(policy.getStartTime())) {
			throw new com.aryn.cloud.common.security.handler.ArynBusinessException("生效时间窗非法");
		}
	}

}
