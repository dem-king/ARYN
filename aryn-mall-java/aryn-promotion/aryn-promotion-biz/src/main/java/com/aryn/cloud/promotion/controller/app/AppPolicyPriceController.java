package com.aryn.cloud.promotion.controller.app;

import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.promotion.api.dto.PolicyPriceQueryDTO;
import com.aryn.cloud.promotion.api.vo.PolicyPriceVO;
import com.aryn.cloud.promotion.service.impl.PricePolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端协议价批量查询（商详 / 购物车 / 商品列表卡片）。
 *
 * <p><b>需登录</b>：协议价是主体/标签 scoped 的合同价，用户身份只从登录态读取，
 * 不接受请求参数传入；未登录网关层即拦截（不进免登白名单）。采购主体/船舶上下文
 * 由 C 端按当前会话上下文传入（与订单计价上下文同源），资格校验在订单链，
 * 本接口只做价格展示——成交仍以下单试算为准。
 *
 * @author aryn
 * @since 2026/11/14
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/policy")
@Tag(description = "app-policy", name = "C端协议价")
public class AppPolicyPriceController {

	private final PricePolicyService pricePolicyService;

	@Operation(summary = "协议价批量查询（需登录；未命中返回空列表）")
	@PostMapping("/prices")
	public Result<List<PolicyPriceVO>> prices(@RequestBody PolicyPriceQueryDTO query) {
		String tenantId = com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder.getTenantId();
		com.aryn.cloud.common.security.entity.ArynUser user = SecurityUtils.getUser();
		String userId = user != null ? user.getUserId() : null;
		return Result.success(pricePolicyService.policyPrices(tenantId, userId, query));
	}

}
