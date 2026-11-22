package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.entity.PromotionActivity;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;
import com.aryn.cloud.promotion.mapper.PromotionActivityMapper;
import com.aryn.cloud.promotion.service.PromotionEngineService;
import com.aryn.cloud.promotion.service.impl.B2bActivityCommandService;
import com.aryn.cloud.promotion.api.dto.B2bActivitySaveDTO;
import com.aryn.cloud.promotion.service.impl.PromotionLockUsage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

	private final PromotionActivityMapper promotionActivityMapper;

	private final B2bActivityCommandService commands;

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
	public Result<PromotionActivity> save(@RequestBody B2bActivitySaveDTO activity) {
		return Result.success(commands.create(activity));
	}

	@SysLog("修改船供营销活动")
	@Operation(summary = "修改活动（仅草稿/暂停，发布后核心规则不可改）")
	@SaCheckPermission("promotion:shipactivity:save")
	@PutMapping("/{id}")
	public Result<PromotionActivity> update(@PathVariable String id, @RequestBody B2bActivitySaveDTO activity) {
		return Result.success(commands.update(id, activity));
	}

	@SysLog("发布船供营销活动")
	@Operation(summary = "发布冲突检索（同类型时间重叠的已发布/暂停活动）")
	@SaCheckPermission("promotion:shipactivity:publish")
	@GetMapping("/{id}/conflicts")
	public Result<List<PromotionActivity>> conflicts(@PathVariable String id) {
		return Result.success(commands.conflicts(id));
	}

	@Operation(summary = "试算：输入模拟购物车与上下文，返回命中活动与金额（不落库）")
	@SaCheckPermission({"promotion:shipactivity:page", "promotion:pricepolicy:page"})
	@PostMapping("/dry-run")
	public Result<PromotionCalculationVO> dryRun(@RequestBody PromotionContextDTO context) {
		context.setTenantId(ArynTenantContextHolder.getTenantId());
		return Result.success(promotionEngineService.preview(context));
	}

	@Operation(summary = "发布活动（force=true 时忽略同类型时间重叠提示）")
	@SaCheckPermission("promotion:shipactivity:publish")
	@PostMapping("/{id}/publish")
	public Result<Void> publish(@PathVariable String id, @RequestParam Long expectedRevision,
			@RequestParam(required = false, defaultValue = "false") boolean force) {
		commands.publish(id, expectedRevision, force);
		return Result.success();
	}

	@SysLog("暂停船供营销活动")
	@Operation(summary = "暂停活动（仅进行中的已发布活动）")
	@SaCheckPermission("promotion:shipactivity:publish")
	@PostMapping("/{id}/pause")
	public Result<Void> pause(@PathVariable String id, @RequestParam Long expectedRevision) {
		commands.pause(id, expectedRevision);
		return Result.success();
	}

	@SysLog("删除船供营销活动")
	@Operation(summary = "删除活动（进行中的已发布活动需先暂停）")
	@SaCheckPermission("promotion:shipactivity:save")
	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable String id, @RequestParam Long expectedRevision) {
		commands.delete(id, expectedRevision);
		return Result.success();
	}

	@SysLog("复制船供营销活动草稿")
	@SaCheckPermission("promotion:shipactivity:save")
	@PostMapping("/{id}/copy")
	public Result<PromotionActivity> copy(@PathVariable String id, @RequestParam Long expectedRevision) {
		return Result.success(commands.copy(id, expectedRevision));
	}

	private PromotionActivity requireActivity(String id) {
		PromotionActivity activity = commands.get(id);
		if (isSystemActivity(activity) || PromotionActivity.TYPE_MEMBER_PRICE.equals(activity.getActivityType())
				|| PromotionActivity.TYPE_CLEARANCE.equals(activity.getActivityType())) {
			throw new ArynBusinessException("系统托管活动仅通过商品专区标签维护");
		}
		return activity;
	}

	private boolean isSystemActivity(PromotionActivity activity) {
		return activity != null && StringUtils.hasText(activity.getActivityCode())
				&& activity.getActivityCode().startsWith("SYS_");
	}

	/** 展示态按时间窗派生，不写回数据库。 */

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

}
