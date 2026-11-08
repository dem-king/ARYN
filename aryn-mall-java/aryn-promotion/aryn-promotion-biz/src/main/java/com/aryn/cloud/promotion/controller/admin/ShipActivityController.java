package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.PromotionEngineService;
import com.aryn.cloud.promotion.service.impl.ActivityPublishGovernanceService;
import com.aryn.cloud.promotion.service.impl.PromotionLockUsage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 船供营销中心：统一活动（阶梯价/整船优惠）配置与发布。
 *
 * <p>发布前校验时间窗与规则结构；发布后核心规则不可修改，只能暂停或新建版本。
 *
 * <p>会员价活动（type=10）已彻底下线，收敛为「商品级专区标签 + 系统托管活动 SYS_MEMBER_ZONE」：
 * 活动列表不展示、按 ID 的详情/修改/发布/暂停/删除全部拦截、新建直接拒绝；
 * 系统托管活动 SYS_MEMBER_ZONE 由商品标签同步服务维护，本控制器不提供任何写入口。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/ship-activity")
@Tag(description = "ship-activity", name = "船供营销活动")
public class ShipActivityController {

	@DubboReference
	private RemoteGoodsSkuService remoteGoodsSkuService;

	private final PromotionActivityMapper promotionActivityMapper;

	private final ActivityPublishGovernanceService publishGovernanceService;

	private final PromotionEngineService promotionEngineService;

	private final PromotionLockUsage promotionLockUsage;

	@Operation(summary = "活动分页")
	@SaCheckPermission("promotion:shipactivity:page")
	@GetMapping("/page")
	public Result<IPage<PromotionActivity>> page(Page<PromotionActivity> page, PromotionActivity query) {
		LocalDateTime now = LocalDateTime.now();
		LambdaQueryWrapper<PromotionActivity> wrapper = Wrappers.lambdaQuery(PromotionActivity.class)
				.eq(PromotionActivity::getTenantId, ArynTenantContextHolder.getTenantId())
				.eq(StringUtils.hasText(query.getActivityType()), PromotionActivity::getActivityType,
						query.getActivityType())
				.like(StringUtils.hasText(query.getActivityName()), PromotionActivity::getActivityName,
						query.getActivityName())
				// 会员价活动（type=10）已下线：手工活动不再支持，系统托管活动由商品标签自动维护，
				// 两者都不在活动列表展示；会员价统一走商品列表「专区打标」/商品编辑「专区标签」
				.ne(PromotionActivity::getActivityType, PromotionActivity.TYPE_MEMBER_PRICE)
				// 系统托管活动（SYS_MEMBER_ZONE/SYS_CLEARANCE_ZONE）同样由商品标签自动维护
				.and(w -> w.isNull(PromotionActivity::getActivityCode)
					.or()
					.notLikeRight(PromotionActivity::getActivityCode, "SYS"));
		// 已结束/待开始是展示态，筛选时翻译为时间窗条件（库内仅存 1草稿/2发布/3暂停）
		if (PromotionActivity.STATUS_ENDED.equals(query.getStatus())) {
			wrapper.eq(PromotionActivity::getStatus, PromotionActivity.STATUS_PUBLISHED)
					.lt(PromotionActivity::getEndTime, now);
		}
		else if (PromotionActivity.STATUS_UPCOMING.equals(query.getStatus())) {
			wrapper.eq(PromotionActivity::getStatus, PromotionActivity.STATUS_PUBLISHED)
					.gt(PromotionActivity::getStartTime, now);
		}
		else {
			wrapper.eq(StringUtils.hasText(query.getStatus()), PromotionActivity::getStatus, query.getStatus());
		}
		wrapper.orderByDesc(PromotionActivity::getCreateTime);
		IPage<PromotionActivity> result = promotionActivityMapper.selectPage(page, wrapper);
		String tenantId = ArynTenantContextHolder.getTenantId();
		result.getRecords().forEach(activity -> {
			activity.setStatus(deriveDisplayStatus(activity, now));
			if (PromotionActivity.TYPE_GIFT.equals(activity.getActivityType())) {
				activity.setConsumedGiftStock(promotionLockUsage.sumGiftQuantity(tenantId, activity.getId()));
			}
		});
		return Result.success(result);
	}

	@Operation(summary = "活动详情")
	@SaCheckPermission("promotion:shipactivity:page")
	@GetMapping("/{id}")
	public Result<PromotionActivity> getById(@PathVariable String id) {
		// requireActivity 拦截系统托管活动与已下线的会员价活动，避免绕过列表直接读取/编辑
		PromotionActivity activity = requireActivity(id);
		activity.setStatus(deriveDisplayStatus(activity, LocalDateTime.now()));
		return Result.success(activity);
	}

	@SysLog("创建船供营销活动")
	@Operation(summary = "创建活动（草稿）")
	@SaCheckPermission("promotion:shipactivity:save")
	@PostMapping
	public Result<PromotionActivity> save(@RequestBody PromotionActivity activity) {
		rejectManualMemberPrice(activity);
		publishGovernanceService.normalizeLongTerm(activity);
		publishGovernanceService.validateRules(activity);
		activity.setId(null);
		activity.setStatus(PromotionActivity.STATUS_DRAFT);
		activity.setConsumedBudget(BigDecimal.ZERO);
		activity.setTenantId(ArynTenantContextHolder.getTenantId());
		activity.setCreateTime(LocalDateTime.now());
		activity.setDelFlag("0");
		fillStackDefaults(activity);
		promotionActivityMapper.insert(activity);
		return Result.success(activity);
	}

	@SysLog("修改船供营销活动")
	@Operation(summary = "修改活动（仅草稿/暂停，发布后核心规则不可改）")
	@SaCheckPermission("promotion:shipactivity:save")
	@PutMapping("/{id}")
	public Result<PromotionActivity> update(@PathVariable String id, @RequestBody PromotionActivity activity) {
		// requireActivity 统一拦截系统托管活动与已下线的会员价活动（入参可能不带 activityType）
		PromotionActivity exists = requireActivity(id);
		if (PromotionActivity.STATUS_PUBLISHED.equals(exists.getStatus())) {
			if (changed(exists.getRules(), activity.getRules()) || changed(exists.getScopeType(), activity.getScopeType())
					|| changed(exists.getScopeValue(), activity.getScopeValue())) {
				throw new ArynBusinessException("已发布活动不可修改规则与适用范围，请暂停或新建版本");
			}
		}
		// 修改入参可能不带活动类型（前端只回传可编辑字段），按库内类型补齐后再规范化长期有效
		if (!StringUtils.hasText(activity.getActivityType())) {
			activity.setActivityType(exists.getActivityType());
		}
		publishGovernanceService.normalizeLongTerm(activity);
		if (StringUtils.hasText(activity.getRules())) {
			publishGovernanceService.validateRules(activity);
		}
		activity.setId(id);
		activity.setTenantId(ArynTenantContextHolder.getTenantId());
		fillStackDefaults(activity);
		promotionActivityMapper.updateById(activity);
		return Result.success(activity);
	}

	/**
	 * 券/积分叠加策略为空时按活动类型补默认：新人专享（type=9）默认不可叠（防 0 元单），
	 * 其余类型默认可叠（维持现网行为）；显式提交的值照常生效。
	 */
	private void fillStackDefaults(PromotionActivity activity) {
		boolean newbie = PromotionActivity.TYPE_NEWBIE_PRICE.equals(activity.getActivityType());
		String couponDefault = newbie ? "0" : "1";
		String pointsDefault = newbie ? "0" : "1";
		if (!StringUtils.hasText(activity.getCouponStackable())) {
			activity.setCouponStackable(couponDefault);
		}
		if (!StringUtils.hasText(activity.getPointsStackable())) {
			activity.setPointsStackable(pointsDefault);
		}
	}

	@SysLog("发布船供营销活动")
	@Operation(summary = "发布冲突检索（同类型时间重叠的已发布/暂停活动）")
	@SaCheckPermission("promotion:shipactivity:publish")
	@GetMapping("/{id}/conflicts")
	public Result<List<PromotionActivity>> conflicts(@PathVariable String id) {
		PromotionActivity activity = requireActivity(id);
		return Result.success(
				publishGovernanceService.listPublishConflicts(ArynTenantContextHolder.getTenantId(), activity));
	}

	@Operation(summary = "试算：输入模拟购物车与上下文，返回命中活动与金额（不落库）")
	@SaCheckPermission("promotion:shipactivity:page")
	@PostMapping("/dry-run")
	public Result<PromotionCalculationVO> dryRun(@RequestBody PromotionContextDTO context) {
		context.setTenantId(ArynTenantContextHolder.getTenantId());
		return Result.success(promotionEngineService.preview(context));
	}

	@Operation(summary = "发布活动（force=true 时忽略同类型时间重叠提示）")
	@SaCheckPermission("promotion:shipactivity:publish")
	@PostMapping("/{id}/publish")
	public Result<Void> publish(@PathVariable String id,
			@RequestParam(required = false, defaultValue = "false") boolean force) {
		// requireActivity 统一拦截系统托管活动与已下线的会员价活动
		PromotionActivity activity = requireActivity(id);
		publishGovernanceService.normalizeLongTerm(activity);
		publishGovernanceService.validateRules(activity);
		assertLadderPricesBelowSales(activity);
		if (activity.getStartTime() == null || activity.getEndTime() == null
				|| !activity.getStartTime().isBefore(activity.getEndTime())) {
			throw new ArynBusinessException("活动开始时间必须早于结束时间");
		}
		if (!activity.getEndTime().isAfter(LocalDateTime.now())) {
			throw new ArynBusinessException("活动结束时间已过，请先调整活动时间再发布");
		}
		if (!force) {
			List<PromotionActivity> conflicts = publishGovernanceService
					.listPublishConflicts(ArynTenantContextHolder.getTenantId(), activity);
			if (!conflicts.isEmpty()) {
				throw new ArynBusinessException("与已发布活动时间重叠：" + conflicts.stream()
						.map(PromotionActivity::getActivityName).toList() + "；运行时同类取最优，确认后可强制发布");
			}
		}
		activity.setStatus(PromotionActivity.STATUS_PUBLISHED);
		activity.setPublishTime(LocalDateTime.now());
		promotionActivityMapper.updateById(activity);
		return Result.success();
	}

	@SysLog("暂停船供营销活动")
	@Operation(summary = "暂停活动（仅进行中的已发布活动）")
	@SaCheckPermission("promotion:shipactivity:publish")
	@PostMapping("/{id}/pause")
	public Result<Void> pause(@PathVariable String id) {
		PromotionActivity activity = requireActivity(id);
		if (!isRunning(activity, LocalDateTime.now())) {
			throw new ArynBusinessException("仅进行中的已发布活动可暂停");
		}
		activity.setStatus(PromotionActivity.STATUS_PAUSED);
		promotionActivityMapper.updateById(activity);
		return Result.success();
	}

	@SysLog("删除船供营销活动")
	@Operation(summary = "删除活动（进行中的已发布活动需先暂停）")
	@SaCheckPermission("promotion:shipactivity:save")
	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable String id) {
		PromotionActivity activity = requireActivity(id);
		if (isRunning(activity, LocalDateTime.now())) {
			throw new ArynBusinessException("进行中的已发布活动请先暂停后再删除");
		}
		promotionActivityMapper.deleteById(id);
		return Result.success();
	}

	private PromotionActivity requireActivity(String id) {
		PromotionActivity activity = promotionActivityMapper.selectOne(Wrappers.lambdaQuery(PromotionActivity.class)
				.eq(PromotionActivity::getTenantId, ArynTenantContextHolder.getTenantId())
				.eq(PromotionActivity::getId, id));
		if (activity == null) {
			throw new ArynBusinessException("活动不存在");
		}
		if (isSystemActivity(activity)) {
			throw new ArynBusinessException("系统托管活动由商品专区标签自动维护，不可手工编辑或删除");
		}
		// 会员价活动已彻底下线（手工不再支持发布/维护，系统活动由标签同步服务维护）：
		// 详情/修改/发布/暂停/删除等按 ID 的入口全部拦截
		if (PromotionActivity.TYPE_MEMBER_PRICE.equals(activity.getActivityType())) {
			throw new ArynBusinessException("会员价活动已下线，请使用商品专区标签维护");
		}
		return activity;
	}

	/**
	 * 会员价活动（type=10）已下线：会员价只通过商品专区标签维护，
	 * 系统托管活动 SYS_MEMBER_ZONE 由标签同步服务自动维护并作为价格载体。
	 *
	 * <p>新建入口（save）写库前拦截；按 ID 的存量活动入口由 {@link #requireActivity(String)} 统一拦截。
	 */
	private void rejectManualMemberPrice(PromotionActivity activity) {
		if (activity != null && PromotionActivity.TYPE_MEMBER_PRICE.equals(activity.getActivityType())) {
			throw new ArynBusinessException(
					"会员价活动已下线：请在商品列表「专区打标」或商品编辑「专区标签」维护");
		}
	}

	private boolean isSystemActivity(PromotionActivity activity) {
		return activity != null && StringUtils.hasText(activity.getActivityCode())
				&& activity.getActivityCode().startsWith("SYS_");
	}

	/** 是否进行中：已发布且当前时间在时间窗内（暂停/删除守卫与展示态派生的基准） */
	private boolean isRunning(PromotionActivity activity, LocalDateTime now) {
		if (!PromotionActivity.STATUS_PUBLISHED.equals(activity.getStatus())) {
			return false;
		}
		boolean started = activity.getStartTime() == null || !activity.getStartTime().isAfter(now);
		boolean notEnded = activity.getEndTime() == null || activity.getEndTime().isAfter(now);
		return started && notEnded;
	}

	/** 管理端展示态派生：已发布活动按时间窗映射为「待开始/已结束」，库内仅存人工操作态 */
	private String deriveDisplayStatus(PromotionActivity activity, LocalDateTime now) {
		if (!PromotionActivity.STATUS_PUBLISHED.equals(activity.getStatus())) {
			return activity.getStatus();
		}
		if (activity.getEndTime() != null && !activity.getEndTime().isAfter(now)) {
			return PromotionActivity.STATUS_ENDED;
		}
		if (activity.getStartTime() != null && activity.getStartTime().isAfter(now)) {
			return PromotionActivity.STATUS_UPCOMING;
		}
		return PromotionActivity.STATUS_PUBLISHED;
	}

	private boolean changed(String before, String after) {
		String a = before == null ? "" : before;
		String b = after == null ? "" : after;
		return !a.equals(b);
	}

	/**
	 * 发布时校验阶梯价每档低于 SKU 当前售价：引擎对不低于基价的档价静默跳过，
	 * 不查商品库会出现「已发布但永不生效」的活动。商品库不可用时阻断发布（fail-closed）。
	 */
	private void assertLadderPricesBelowSales(PromotionActivity activity) {
		if (!PromotionActivity.TYPE_LADDER_PRICE.equals(activity.getActivityType())
				|| !StringUtils.hasText(activity.getRules())) {
			return;
		}
		JSONArray skuLadders = JSONUtil.parseObj(activity.getRules()).getJSONArray("skuLadders");
		if (skuLadders == null || skuLadders.isEmpty()) {
			return;
		}
		List<String> skuIds = skuLadders.stream()
			.map(element -> ((JSONObject) element).getStr("skuId"))
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		Map<String, BigDecimal> salesBySku;
		try {
			salesBySku = remoteGoodsSkuService.getBySkuIds(skuIds).stream()
				.collect(Collectors.toMap(GoodsSku::getId, GoodsSku::getSalesPrice, (a, b) -> a));
		}
		catch (Exception ex) {
			throw new ArynBusinessException("商品服务不可用，无法校验阶梯价与售价关系，请稍后重试");
		}
		for (Object element : skuLadders) {
			JSONObject skuLadder = (JSONObject) element;
			String skuId = skuLadder.getStr("skuId");
			BigDecimal salesPrice = salesBySku.get(skuId);
			if (salesPrice == null) {
				throw new ArynBusinessException("阶梯价 SKU " + skuId + " 不存在，无法发布");
			}
			JSONArray ladders = skuLadder.getJSONArray("ladders");
			if (ladders == null) {
				continue;
			}
			for (Object ladderElement : ladders) {
				JSONObject ladder = (JSONObject) ladderElement;
				BigDecimal unitPrice = ladder.getBigDecimal("unitPrice");
				if (unitPrice != null && unitPrice.compareTo(salesPrice) >= 0) {
					throw new ArynBusinessException("SKU " + skuId + " 档价 " + unitPrice + " 不低于当前售价 "
							+ salesPrice + "，发布后不会生效，请先调整");
				}
			}
		}
	}

}
