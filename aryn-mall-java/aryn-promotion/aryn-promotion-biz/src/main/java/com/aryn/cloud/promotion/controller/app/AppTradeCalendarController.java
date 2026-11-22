package com.aryn.cloud.promotion.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.promotion.api.entity.PromoCalendar;
import com.aryn.cloud.promotion.api.vo.AppCalendarSpecialVO;
import com.aryn.cloud.promotion.service.impl.PromoCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端采购档期与档期特供（档期楼层/特供会场）。
 *
 * <p><b>免登可访问</b>（方案 §11.7 双端白名单，2026-11-15 审查修复）：档期与特供价
 * 是全局公开价格层，游客可见；展示口径与成交试算同源（活动窗 + 档期窗 + 剩余限量
 * + 场景/船舶圈定过滤），限量耗尽前置剔除。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/calendar")
@Tag(description = "app-calendar", name = "C端采购档期")
public class AppTradeCalendarController {

	private final PromoCalendarService promoCalendarService;

	@Operation(summary = "生效中的采购档期列表")
	@GetMapping("/list")
	public Result<List<PromoCalendar>> list() {
		return Result.success(promoCalendarService.effectiveCalendars(ArynTenantContextHolder.getTenantId()));
	}

	@Operation(summary = "档期特供列表（calendarId 为空=全部生效档期；按场景/船舶圈定过滤）")
	@GetMapping("/specials")
	public Result<List<AppCalendarSpecialVO>> specials(
			@RequestParam(value = "calendarId", required = false) String calendarId,
			@RequestParam(value = "purchaseScene", required = false) String purchaseScene,
			@RequestParam(value = "vesselId", required = false) String vesselId) {
		return Result.success(promoCalendarService.specials(ArynTenantContextHolder.getTenantId(), calendarId,
				purchaseScene, vesselId));
	}

}
