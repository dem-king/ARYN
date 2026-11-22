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
 * <p><b>免登可访问</b>（方案 §11.7 双端白名单）：游客请求清空主体上下文，只保留
 * 客群标签维度（游客无标签自然返空），不会泄露合同价；登录用户的船舶/企业上下文
 * 逐一校验本人成员资格（{@code sanitizeSubjectContext}，fail-closed），
 * 防越权窥探他主体合同价。成交仍以下单试算为准。
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

	@Operation(summary = "协议价批量查询（免登；游客/非成员上下文自动净化，未命中返回空列表）")
	@PostMapping("/prices")
	public Result<List<PolicyPriceVO>> prices(@RequestBody PolicyPriceQueryDTO query) {
		String tenantId = com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder.getTenantId();
		com.aryn.cloud.common.security.entity.ArynUser user = SecurityUtils.getUser();
		String userId = user != null ? user.getUserId() : null;
		pricePolicyService.sanitizeSubjectContext(tenantId, userId, query);
		return Result.success(pricePolicyService.policyPrices(tenantId, userId, query));
	}

}
