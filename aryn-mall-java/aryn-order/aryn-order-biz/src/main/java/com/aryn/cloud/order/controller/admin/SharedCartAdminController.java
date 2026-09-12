package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 共享购物车管理端：查看进行中的同船采购车（成员与明细），辅助运营跟进整船订单。
 *
 * @author aryn
 * @since 2026/9/12
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/shared-cart")
@Tag(description = "shared-cart-admin", name = "共享购物车管理")
public class SharedCartAdminController {

	private final SharedCartMapper sharedCartMapper;

	private final SharedCartMemberMapper sharedCartMemberMapper;

	private final SharedCartItemMapper sharedCartItemMapper;

	@Operation(summary = "共享购物车分页")
	@SaCheckPermission("sharedcart:page")
	@GetMapping("/page")
	public Result<IPage<SharedCart>> page(Page<SharedCart> page, SharedCart query) {
		return Result.success(sharedCartMapper.selectPage(page, Wrappers.lambdaQuery(SharedCart.class)
				.eq(SharedCart::getTenantId, ArynTenantContextHolder.getTenantId())
				.eq(StringUtils.hasText(query.getStatus()), SharedCart::getStatus, query.getStatus())
				.eq(StringUtils.hasText(query.getVesselCallId()), SharedCart::getVesselCallId,
						query.getVesselCallId())
				.like(StringUtils.hasText(query.getCartNo()), SharedCart::getCartNo, query.getCartNo())
				.orderByDesc(SharedCart::getCreateTime)));
	}

	@Operation(summary = "共享购物车详情（成员+明细）")
	@SaCheckPermission("sharedcart:get")
	@GetMapping("/{id}")
	public Result<Map<String, Object>> detail(@PathVariable String id) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		SharedCart cart = sharedCartMapper.selectOne(Wrappers.lambdaQuery(SharedCart.class)
				.eq(SharedCart::getTenantId, tenantId)
				.eq(SharedCart::getId, id));
		if (cart == null) {
			return Result.fail("共享购物车不存在");
		}
		List<SharedCartMember> members = sharedCartMemberMapper.selectList(
				Wrappers.lambdaQuery(SharedCartMember.class)
						.eq(SharedCartMember::getTenantId, tenantId)
						.eq(SharedCartMember::getCartId, id)
						.orderByAsc(SharedCartMember::getJoinedTime));
		List<SharedCartItem> items = sharedCartItemMapper.selectList(Wrappers.lambdaQuery(SharedCartItem.class)
				.eq(SharedCartItem::getTenantId, tenantId)
				.eq(SharedCartItem::getCartId, id)
				.orderByAsc(SharedCartItem::getCreateTime));
		Map<String, Object> detail = new HashMap<>();
		detail.put("cart", cart);
		detail.put("members", members);
		detail.put("items", items);
		return Result.success(detail);
	}

}
