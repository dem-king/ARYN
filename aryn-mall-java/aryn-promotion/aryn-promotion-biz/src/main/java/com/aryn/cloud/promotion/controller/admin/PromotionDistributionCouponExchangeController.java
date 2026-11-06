package com.aryn.cloud.promotion.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.promotion.api.entity.DistributionCouponExchange;
import com.aryn.cloud.promotion.api.vo.DistributionCouponCostVO;
import com.aryn.cloud.promotion.api.vo.DistributionCouponExchangeVO;
import com.aryn.cloud.promotion.service.IDistributionCouponExchangeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * 分销券兑换记录（后台）
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/distribution/coupon/exchange")
@Tag(name = "分销券兑换记录", description = "分销佣金兑换优惠券记录与券成本")
public class PromotionDistributionCouponExchangeController {

	private final IDistributionCouponExchangeService distributionCouponExchangeService;

	@Operation(summary = "兑换记录分页")
	@SaCheckPermission("promotion:distributioncouponexchange:page")
	@GetMapping("/page")
	public Result<IPage<DistributionCouponExchangeVO>> page(Page<DistributionCouponExchange> page,
			DistributionCouponExchange query,
			@RequestParam(required = false) String createTimeStart,
			@RequestParam(required = false) String createTimeEnd) {
		LocalDateTime start = parseDateTime(createTimeStart, false);
		LocalDateTime end = parseDateTime(createTimeEnd, true);
		return Result.success(distributionCouponExchangeService.getAdminPage(page, query, start, end));
	}

	@Operation(summary = "券成本汇总")
	@SaCheckPermission("promotion:distributioncouponexchange:page")
	@GetMapping("/statistics")
	public Result<DistributionCouponCostVO> statistics(DistributionCouponExchange query,
			@RequestParam(required = false) String createTimeStart,
			@RequestParam(required = false) String createTimeEnd) {
		LocalDateTime start = parseDateTime(createTimeStart, false);
		LocalDateTime end = parseDateTime(createTimeEnd, true);
		return Result.success(distributionCouponExchangeService.getStatistics(query, start, end));
	}

	/**
	 * 兼容前端日期控件常见的三种格式：yyyy-MM-dd、yyyy-MM-dd HH:mm:ss、ISO yyyy-MM-ddTHH:mm:ss。
	 */
	private LocalDateTime parseDateTime(String text, boolean endOfDay) {
		if (!StringUtils.hasText(text)) {
			return null;
		}
		String value = text.trim();
		try {
			if (value.length() == 10) {
				LocalDate date = LocalDate.parse(value);
				return endOfDay ? date.atTime(23, 59, 59) : date.atStartOfDay();
			}
			String normalized = value.replace(' ', 'T');
			if (normalized.length() == 16) {
				normalized = normalized + ":00";
			}
			return LocalDateTime.parse(normalized);
		}
		catch (DateTimeParseException e) {
			throw new ArynBusinessException("时间格式不正确：" + text);
		}
	}

}
