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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
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
		validateRules(activity);
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
			validateRules(activity);
		}
		activity.setId(id);
		activity.setTenantId(SecurityUtils.getTenantId());
		promotionActivityMapper.updateById(activity);
		return Result.success(activity);
	}

	@SysLog("发布船供营销活动")
	@Operation(summary = "发布活动")
	@SaCheckPermission("promotion:shipactivity:publish")
	@PostMapping("/{id}/publish")
	public Result<Void> publish(@PathVariable String id) {
		PromotionActivity activity = requireActivity(id);
		validateRules(activity);
		if (activity.getStartTime() == null || activity.getEndTime() == null
				|| !activity.getStartTime().isBefore(activity.getEndTime())) {
			throw new ArynBusinessException("活动开始时间必须早于结束时间");
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

	/**
	 * 发布前规则结构检查：阶梯价 ladders[{minQty,unitPrice}]，整船优惠 tiers[{minAmount,discountAmount}]。
	 */
	private void validateRules(PromotionActivity activity) {
		if (!StringUtils.hasText(activity.getRules())) {
			throw new ArynBusinessException("活动规则不能为空");
		}
		try {
			JSONObject rules = JSONUtil.parseObj(activity.getRules());
			if (PromotionActivity.TYPE_LADDER_PRICE.equals(activity.getActivityType())) {
				JSONArray ladders = rules.getJSONArray("ladders");
				if (ladders == null || ladders.isEmpty()) {
					throw new ArynBusinessException("阶梯价活动必须配置 ladders 档位");
				}
				for (Object element : ladders) {
					JSONObject ladder = (JSONObject) element;
					if (ladder.getInt("minQty") == null || ladder.getBigDecimal("unitPrice") == null
							|| ladder.getBigDecimal("unitPrice").signum() <= 0) {
						throw new ArynBusinessException("阶梯价档位必须包含正数 unitPrice 与 minQty");
					}
				}
			}
			else if (PromotionActivity.TYPE_SHIP_WHOLE_DISCOUNT.equals(activity.getActivityType())) {
				JSONArray tiers = rules.getJSONArray("tiers");
				if (tiers == null || tiers.isEmpty()) {
					throw new ArynBusinessException("整船优惠活动必须配置 tiers 档位");
				}
				for (Object element : tiers) {
					JSONObject tier = (JSONObject) element;
					if (tier.getBigDecimal("minAmount") == null || tier.getBigDecimal("discountAmount") == null
							|| tier.getBigDecimal("discountAmount").signum() <= 0) {
						throw new ArynBusinessException("整船优惠档位必须包含正数 discountAmount 与 minAmount");
					}
				}
			}
			else {
				throw new ArynBusinessException("暂不支持的活动类型：" + activity.getActivityType());
			}
		}
		catch (ArynBusinessException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new ArynBusinessException("活动规则 JSON 不合法");
		}
	}

	private boolean changed(String before, String after) {
		String a = before == null ? "" : before;
		String b = after == null ? "" : after;
		return !a.equals(b);
	}

}
