package com.aryn.cloud.promotion.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.vo.AppMemberPriceVO;
import com.aryn.cloud.promotion.service.IMemberPriceActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端会员价查询（商详 / 购物车 / 商品列表二次聚合）。
 *
 * <p><b>可选登录</b>：与 {@code /app/newbie/prices} 同一免登语义——未登录也返回活动配置的
 * 会员价（营销引导），只是下发的 {@code eligible} 为 null、不含用户等级字段；
 * 用户身份只从登录态读取，不接受请求参数传入（否则改一个参数就能查别人的等级/资格）。
 *
 * <p>两种入参形态，<b>skuIds 优先</b>：
 * <ul>
 *   <li>{@code ?skuIds=a,b}：商详 / 购物车等已知具体 SKU 的页面（原有契约，行为不变）；</li>
 *   <li>{@code ?spuIds=a,b}：商品列表卡片只知道 SPU（卡片价是 SPU 聚合价），后端把活动配置的
 *       SKU 经商品库回填 spuId 后按请求 SPU 过滤；同一 SPU 可能返回多条 item（不同规格），
 *       由 C 端按卡片展示价对应的 SKU 精确匹配；</li>
 *   <li>两者都空：直接返回 {@code Result.success(null)}（不再 400，列表页可能按需才带参数）。</li>
 * </ul>
 *
 * <p>boot 模式：入口在 aryn-boot 的 {@code secure.ignore.urls} 白名单中（{@code /app/member/prices}，
 * 与 {@code /app/newbie/prices} 相邻登记）；cloud 模式：网关白名单在 Nacos
 * {@code aryn-gateway-dev.yml} 中维护，已在 db/cloud/164 号增量脚本登记
 * {@code /promotion/app/member/prices} 与 boot 兼容前缀/路由
 * （与 promotion 域其余 C 端公开读接口逐条列举，不使用域级通配，避免顺带放开写接口）。
 *
 * @author aryn
 * @since 2026/10/25
 */
@Slf4j
@AllArgsConstructor
@RestController
@RequestMapping("/app/member")
@Tag(description = "app-member", name = "C端会员价")
public class AppMemberPriceController {

	private final IMemberPriceActivityService memberPriceActivityService;

	/**
	 * 批量查询会员价：按 SKU（商详 / 购物车）或按 SPU（商品列表，可选）。
	 *
	 * <p>兼容前端「逗号分隔单个参数」的拼接口径（{@code ?skuIds=a,b} / {@code ?spuIds=a,b} → List），
	 * 与新人价接口一致，不额外要求登录。
	 */
	@Operation(summary = "会员价批量查询（skuIds 优先；无 skuIds 时按 spuIds；可选登录）")
	@GetMapping("/prices")
	public Result<AppMemberPriceVO> prices(
			@RequestParam(value = "skuIds", required = false) List<String> skuIds,
			@RequestParam(value = "spuIds", required = false) List<String> spuIds) {
		ArynUser user = SecurityUtils.getUser();
		String userId = user != null ? user.getUserId() : null;
		// 入参优先级（契约固定）：有精确 SKU 走原逻辑（行为完全不变）→ 只有 SPU 走列表聚合 → 都空返回 null
		if (!CollectionUtils.isEmpty(skuIds)) {
			return Result.success(memberPriceActivityService.getSkuPrices(skuIds, userId));
		}
		if (!CollectionUtils.isEmpty(spuIds)) {
			return Result.success(memberPriceActivityService.getSpuPrices(spuIds, userId));
		}
		return Result.success(null);
	}

}
