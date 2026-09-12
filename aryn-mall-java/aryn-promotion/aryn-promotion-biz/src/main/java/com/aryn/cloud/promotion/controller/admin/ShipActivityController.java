package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.impl.ActivityPublishGovernanceService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

/**
 * 船供营销中心：统一活动（阶梯价/整船优惠）配置与发布。
 *
 * <p>发布前校验时间窗与规则结构；发布后核心规则不可修改，只能暂停或新建版本。
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

	private final PromotionActivityMapper promotionActivityMapper;

	private final ActivityPublishGovernanceService publishGovernanceService;

	@Operation(summary = "活动分页")
	@SaCheckPermission("promotion:shipactivity:page")
	@GetMapping("/page")
	public Result<IPage<PromotionActivity>> page(Page<PromotionActivity> page, PromotionActivity query) {
		return Result.success(promotionActivityMapper.selectPage(page, Wrappers.lambdaQuery(PromotionActivity.class)
				.eq(PromotionActivity::getTenantId, SecurityUtils.getTenantId())
				.eq(StringUtils.hasText(query.getStatus()), PromotionActivity::getStatus, query.getStatus())
				.eq(StringUtils.hasText(query.getActivityType()), PromotionActivity::getActivityType,
						query.getActivityType())
				.like(StringUtils.hasText(query.getActivityName()), PromotionActivity::getActivityName,
						query.getActivityName())
				.orderByDesc(PromotionActivity::getCreateTime)));
	}

	@Operation(summary = "活动详情")
	@SaCheckPermission("promotion:shipactivity:page")
	@GetMapping("/{id}")
	public Result<PromotionActivity> getById(@PathVariable String id) {
		return Result.success(promotionActivityMapper.selectOne(Wrappers.lambdaQuery(PromotionActivity.class)
				.eq(PromotionActivity::getTenantId, SecurityUtils.getTenantId())
				.eq(PromotionActivity::getId, id)));
	}

	@SysLog("创建船供营销活动")
	@Operation(summary = "创建活动（草稿）")
	@SaCheckPermission("promotion:shipactivity:save")
	@PostMapping
	public Result<PromotionActivity> save(@RequestBody PromotionActivity activity) {
		publishGovernanceService.validateRules(activity);
		activity.setId(null);
		activity.setStatus(PromotionActivity.STATUS_DRAFT);
		activity.setTenantId(ArynTenantContextHolder.getTenantId());
		activity.setCreateTime(LocalDateTime.now());
		activity.setDelFlag("0");
		promotionActivityMapper.insert(activity);
		return Result.success(activity);
	}

	@SysLog("修改船供营销活动")
	@Operation(summary = "修改活动（仅草稿/暂停，发布后核心规则不可改）")
	@SaCheckPermission("promotion:shipactivity:save")
	@PutMapping("/{id}")
	public Result<PromotionActivity> update(@PathVariable String id, @RequestBody PromotionActivity activity) {
		PromotionActivity exists = requireActivity(id);
		if (PromotionActivity.STATUS_PUBLISHED.equals(exists.getStatus())) {
			if (changed(exists.getRules(), activity.getRules()) || changed(exists.getScopeType(), activity.getScopeType())
					|| changed(exists.getScopeValue(), activity.getScopeValue())) {
				throw new ArynBusinessException("已发布活动不可修改规则与适用范围，请暂停或新建版本");
			}
		}
		if (StringUtils.hasText(activity.getRules())) {
			publishGovernanceService.validateRules(activity);
		}
		activity.setId(id);
		activity.setTenantId(SecurityUtils.getTenantId());
		promotionActivityMapper.updateById(activity);
		return Result.success(activity);
	}

	@SysLog("发布船供营销活动")
	@Operation(summary = "发布冲突检索（同类型时间重叠的已发布/暂停活动）")
	@SaCheckPermission("promotion:shipactivity:publish")
	@GetMapping("/{id}/conflicts")
	public Result<List<PromotionActivity>> conflicts(@PathVariable String id) {
		PromotionActivity activity = requireActivity(id);
		return Result.success(
				publishGovernanceService.listPublishConflicts(SecurityUtils.getTenantId(), activity));
	}

	@Operation(summary = "发布活动（force=true 时忽略同类型时间重叠提示）")
	@SaCheckPermission("promotion:shipactivity:publish")
	@PostMapping("/{id}/publish")
	public Result<Void> publish(@PathVariable String id,
			@RequestParam(required = false, defaultValue = "false") boolean force) {
		PromotionActivity activity = requireActivity(id);
		publishGovernanceService.validateRules(activity);
		if (activity.getStartTime() == null || activity.getEndTime() == null
				|| !activity.getStartTime().isBefore(activity.getEndTime())) {
			throw new ArynBusinessException("活动开始时间必须早于结束时间");
		}
		if (!force) {
			List<PromotionActivity> conflicts = publishGovernanceService
					.listPublishConflicts(SecurityUtils.getTenantId(), activity);
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
	@Operation(summary = "暂停活动")
	@SaCheckPermission("promotion:shipactivity:publish")
	@PostMapping("/{id}/pause")
	public Result<Void> pause(@PathVariable String id) {
		PromotionActivity activity = requireActivity(id);
		activity.setStatus(PromotionActivity.STATUS_PAUSED);
		promotionActivityMapper.updateById(activity);
		return Result.success();
	}

	@SysLog("删除船供营销活动")
	@Operation(summary = "删除活动（草稿/暂停）")
	@SaCheckPermission("promotion:shipactivity:save")
	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable String id) {
		PromotionActivity activity = requireActivity(id);
		if (PromotionActivity.STATUS_PUBLISHED.equals(activity.getStatus())) {
			throw new ArynBusinessException("已发布活动请先暂停后再删除");
		}
		promotionActivityMapper.deleteById(id);
		return Result.success();
	}

	private PromotionActivity requireActivity(String id) {
		PromotionActivity activity = promotionActivityMapper.selectOne(Wrappers.lambdaQuery(PromotionActivity.class)
				.eq(PromotionActivity::getTenantId, SecurityUtils.getTenantId())
				.eq(PromotionActivity::getId, id));
		if (activity == null) {
			throw new ArynBusinessException("活动不存在");
		}
		return activity;
	}

	private boolean changed(String before, String after) {
		String a = before == null ? "" : before;
		String b = after == null ? "" : after;
		return !a.equals(b);
	}

}
