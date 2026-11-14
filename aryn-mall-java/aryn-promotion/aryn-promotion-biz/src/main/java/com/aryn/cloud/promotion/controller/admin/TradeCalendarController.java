package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.PromoCalendar;
import com.aryn.cloud.promotion.service.impl.PromoCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 采购档期管理：B2B 经营节奏锚点（周度/靠港/自然月/自定义时间窗）。
 *
 * <p>活动经 promotion_activity.calendar_id 归属档期；档期停用/窗口外时其下活动
 * （含档期特供）自动退出试算。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/trade-calendar")
@Tag(description = "trade-calendar", name = "采购档期")
public class TradeCalendarController {

	private final PromoCalendarService promoCalendarService;

	@Operation(summary = "档期列表")
	@SaCheckPermission("promotion:calendar:page")
	@GetMapping("/list")
	public Result<List<PromoCalendar>> list(
			@RequestParam(value = "status", required = false) String status,
			@RequestParam(value = "keyword", required = false) String keyword) {
		return Result.success(promoCalendarService.list(ArynTenantContextHolder.getTenantId(), status, keyword));
	}

	@Operation(summary = "档期详情")
	@SaCheckPermission("promotion:calendar:page")
	@GetMapping("/{id}")
	public Result<PromoCalendar> get(@PathVariable String id) {
		return Result.success(promoCalendarService.get(ArynTenantContextHolder.getTenantId(), id));
	}

	@SysLog("新建采购档期")
	@Operation(summary = "新建档期")
	@SaCheckPermission("promotion:calendar:save")
	@PostMapping
	public Result<PromoCalendar> save(@RequestBody PromoCalendar calendar) {
		return Result.success(promoCalendarService.save(ArynTenantContextHolder.getTenantId(), calendar));
	}

	@SysLog("修改采购档期")
	@Operation(summary = "修改档期")
	@SaCheckPermission("promotion:calendar:save")
	@PutMapping("/{id}")
	public Result<PromoCalendar> update(@PathVariable String id, @RequestBody PromoCalendar calendar) {
		return Result.success(promoCalendarService.update(ArynTenantContextHolder.getTenantId(), id, calendar));
	}

	@SysLog("切换档期状态")
	@Operation(summary = "切换档期状态（1生效 2停用 0草稿）")
	@SaCheckPermission("promotion:calendar:save")
	@PutMapping("/{id}/status/{targetStatus}")
	public Result<PromoCalendar> changeStatus(@PathVariable String id, @PathVariable String targetStatus) {
		return Result.success(promoCalendarService.changeStatus(
				ArynTenantContextHolder.getTenantId(), id, targetStatus));
	}

	@SysLog("删除采购档期")
	@Operation(summary = "删除档期（逻辑删除）")
	@SaCheckPermission("promotion:calendar:del")
	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable String id) {
		promoCalendarService.delete(ArynTenantContextHolder.getTenantId(), id);
		return Result.success();
	}

}
