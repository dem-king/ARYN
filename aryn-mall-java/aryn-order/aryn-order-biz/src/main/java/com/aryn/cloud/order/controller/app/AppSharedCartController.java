package com.aryn.cloud.order.controller.app;

import com.aryn.cloud.common.core.enums.DeviceTypeEnum;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartMemberNameDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartVO;
import com.aryn.cloud.order.service.ISharedCartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 共享购物车 C 端接口：同船多海员分别加购，授权人员统一确认提交整船订单。
 *
 * @author aryn
 * @since 2026/9/12
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/app/shared-cart")
@Tag(description = "shared-cart-app", name = "共享购物车")
public class AppSharedCartController {

	private final ISharedCartService sharedCartService;

	@Operation(summary = "创建共享购物车（发起人）")
	@PostMapping
	public Result<SharedCart> create(@Valid @RequestBody SharedCartCreateDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.create(ArynTenantContextHolder.getTenantId(), user.getUserId(), dto));
	}

	@Operation(summary = "我参与的共享购物车列表（我发起或我被邀请）")
	@GetMapping("/my")
	public Result<List<SharedCartVO>> myCarts() {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(
				sharedCartService.listMyCarts(ArynTenantContextHolder.getTenantId(), user.getUserId()));
	}

	@Operation(summary = "购物车详情（仅成员可见）")
	@GetMapping("/{id}")
	public Result<SharedCartVO> detail(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(
				sharedCartService.getCartDetail(ArynTenantContextHolder.getTenantId(), user.getUserId(), id));
	}

	@Operation(summary = "购物车成员列表")
	@GetMapping("/{id}/members")
	public Result<List<SharedCartMember>> members(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		sharedCartService.getCartForUser(ArynTenantContextHolder.getTenantId(), user.getUserId(), id);
		return Result.success(sharedCartService.listMembers(ArynTenantContextHolder.getTenantId(), id));
	}

	@Operation(summary = "生成分享令牌（发起人）——用于微信群转发自助加入")
	@PostMapping("/{id}/share")
	public Result<String> share(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.ensureShareToken(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), id));
	}

	@Operation(summary = "凭分享令牌加入（群成员点击卡片后调用，自动补建船舶成员关系）")
	@PostMapping("/join")
	public Result<String> joinByToken(@RequestParam("token") String token) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		// 只回传购物车 ID 供前端跳转，不回显实体（避免把 share_token 等字段回抛）
		SharedCart cart = sharedCartService.joinByShareToken(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), token);
		return Result.success(cart.getId());
	}

	@Operation(summary = "设置我的展示姓名（加入时填写，用于配送贴标签）")
	@PutMapping("/{id}/members/me/name")
	public Result<SharedCartMember> updateMyDisplayName(@PathVariable String id,
			@Valid @RequestBody SharedCartMemberNameDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.updateMemberDisplayName(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), id, dto.getDisplayName()));
	}

	@Operation(summary = "购物车明细列表")
	@GetMapping("/{id}/items")
	public Result<List<SharedCartItem>> items(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		sharedCartService.getCartForUser(ArynTenantContextHolder.getTenantId(), user.getUserId(), id);
		return Result.success(sharedCartService.listItems(ArynTenantContextHolder.getTenantId(), id));
	}

	@Operation(summary = "邀请成员（发起人）")
	@PostMapping("/{id}/members")
	public Result<SharedCartMember> invite(@PathVariable String id, @RequestParam String memberUserId,
			@RequestParam(required = false, defaultValue = "2") String memberRole) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.inviteMember(ArynTenantContextHolder.getTenantId(), id, user.getUserId(),
				memberUserId, memberRole));
	}

	@Operation(summary = "添加自己的明细")
	@PostMapping("/{id}/items")
	public Result<SharedCartItem> addItem(@PathVariable String id, @Valid @RequestBody SharedCartItemDTO itemDTO) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.addItem(ArynTenantContextHolder.getTenantId(), user.getUserId(), id,
				itemDTO));
	}

	@Operation(summary = "修改自己的明细")
	@PutMapping("/{id}/items/{itemId}")
	public Result<SharedCartItem> updateItem(@PathVariable String id, @PathVariable String itemId,
			@Valid @RequestBody SharedCartItemDTO itemDTO) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.updateItem(ArynTenantContextHolder.getTenantId(), user.getUserId(), id,
				itemId, itemDTO));
	}

	@Operation(summary = "移除自己的明细")
	@DeleteMapping("/{id}/items/{itemId}")
	public Result<Void> removeItem(@PathVariable String id, @PathVariable String itemId) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		sharedCartService.removeItem(ArynTenantContextHolder.getTenantId(), user.getUserId(), id, itemId);
		return Result.success();
	}

	@Operation(summary = "确认人统一提交，生成整船订单（幂等）")
	@PostMapping("/{id}/confirm")
	public Result<String> confirm(@PathVariable String id, @RequestBody(required = false) SharedCartConfirmDTO dto) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		return Result.success(sharedCartService.confirmAndCreateOrder(ArynTenantContextHolder.getTenantId(),
				user.getUserId(), id, dto));
	}

	@Operation(summary = "发起人关闭购物车")
	@PostMapping("/{id}/close")
	public Result<Void> close(@PathVariable String id) {
		ArynUser user = SecurityUtils.requireUser(DeviceTypeEnum.TOC);
		sharedCartService.close(ArynTenantContextHolder.getTenantId(), user.getUserId(), id);
		return Result.success();
	}

}
