package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 共享购物车管理端：查看进行中的同船采购车（成员与明细），辅助运营跟进整船订单。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/shared-cart")
@Tag(description = "shared-cart-admin", name = "共享购物车管理")
public class SharedCartAdminController {

	private final SharedCartMapper sharedCartMapper;

	private final SharedCartMemberMapper sharedCartMemberMapper;

	private final SharedCartItemMapper sharedCartItemMapper;

	@DubboReference
	private RemoteGoodsSkuService remoteGoodsSkuService;

	@DubboReference
	private RemoteGoodsSpuService remoteGoodsSpuService;

	@DubboReference
	private RemoteMallUserService remoteMallUserService;

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

	@Operation(summary = "共享购物车详情（成员+明细，回填商品名与用户昵称）")
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

		// 成员与明细的来源人统一解析昵称，避免前端裸露用户 ID
		Map<String, String> userNames = loadUserNames(members, items);
		members.forEach(member -> member.setDisplayName(resolveMemberName(member, userNames)));

		Map<String, GoodsSku> skuMap = loadSkuMap(items);
		Map<String, GoodsSpu> spuMap = loadSpuMap(items);
		List<Map<String, Object>> itemRows = items.stream()
				.map(item -> toItemRow(item, skuMap, spuMap, userNames))
				.toList();

		Map<String, Object> detail = new HashMap<>();
		detail.put("cart", cart);
		detail.put("members", members);
		detail.put("items", itemRows);
		return Result.success(detail);
	}

	/**
	 * 批量解析成员/明细涉及用户的昵称。远程失败降级为空表：
	 * 名称缺失时调用方回退「用户+ID 后 6 位」，不因用户域抖动打不开详情。
	 */
	private Map<String, String> loadUserNames(List<SharedCartMember> members, List<SharedCartItem> items) {
		List<String> userIds = Stream
				.concat(members.stream().map(SharedCartMember::getUserId),
						items.stream().map(SharedCartItem::getUserId))
				.filter(StringUtils::hasText)
				.distinct()
				.toList();
		if (userIds.isEmpty()) {
			return Map.of();
		}
		try {
			List<UserInfoVO> users = remoteMallUserService.getUserByIds(userIds);
			if (users == null || users.isEmpty()) {
				return Map.of();
			}
			Map<String, String> names = new HashMap<>();
			for (UserInfoVO user : users) {
				if (user != null && StringUtils.hasText(user.getNickname())) {
					names.put(user.getId(), user.getNickname());
				}
			}
			return names;
		}
		catch (Exception exception) {
			log.warn("共享购物车详情补齐用户昵称失败，降级为 ID 兜底", exception);
			return Map.of();
		}
	}

	/**
	 * 成员展示名兜底链：成员填写姓名 → 商城昵称 → 「用户」+ID 后 6 位。
	 */
	private String resolveMemberName(SharedCartMember member, Map<String, String> userNames) {
		if (StringUtils.hasText(member.getDisplayName())) {
			return member.getDisplayName();
		}
		String nickname = userNames.get(member.getUserId());
		if (StringUtils.hasText(nickname)) {
			return nickname;
		}
		String userId = member.getUserId();
		if (!StringUtils.hasText(userId)) {
			return null;
		}
		return "用户" + userId.substring(Math.max(0, userId.length() - 6));
	}

	/**
	 * 批量取 SKU（含规格）。远程失败降级为空表：明细少了规格仍应能显示数量与状态。
	 */
	private Map<String, GoodsSku> loadSkuMap(List<SharedCartItem> items) {
		List<String> skuIds = items.stream().map(SharedCartItem::getSkuId).filter(StringUtils::hasText).distinct()
				.toList();
		if (skuIds.isEmpty()) {
			return Map.of();
		}
		try {
			List<GoodsSku> skus = remoteGoodsSkuService.getSkuByIds(skuIds);
			Map<String, GoodsSku> map = new HashMap<>();
			for (GoodsSku sku : skus) {
				map.put(sku.getId(), sku);
			}
			return map;
		}
		catch (Exception exception) {
			log.warn("共享购物车详情补齐 SKU 信息失败，降级为仅返回ID", exception);
			return Map.of();
		}
	}

	/**
	 * 批量取 SPU（商品名）。远程失败降级为空表，商品名缺失时前端显示占位符。
	 */
	private Map<String, GoodsSpu> loadSpuMap(List<SharedCartItem> items) {
		List<String> spuIds = items.stream().map(SharedCartItem::getSpuId).filter(StringUtils::hasText).distinct()
				.toList();
		if (spuIds.isEmpty()) {
			return Map.of();
		}
		try {
			List<GoodsSpu> spus = remoteGoodsSpuService.getSpuByIds(spuIds);
			Map<String, GoodsSpu> map = new HashMap<>();
			for (GoodsSpu spu : spus) {
				map.put(spu.getId(), spu);
			}
			return map;
		}
		catch (Exception exception) {
			log.warn("共享购物车详情补齐商品名称失败，降级为仅返回ID", exception);
			return Map.of();
		}
	}

	/**
	 * 明细行组装：实体字段平铺后补商品名/规格/来源成员名，避免向管理端裸露 ID 列表。
	 */
	private Map<String, Object> toItemRow(SharedCartItem item, Map<String, GoodsSku> skuMap,
			Map<String, GoodsSpu> spuMap, Map<String, String> userNames) {
		Map<String, Object> row = new LinkedHashMap<>(BeanUtil.beanToMap(item));
		GoodsSpu spu = spuMap.get(item.getSpuId());
		GoodsSku sku = skuMap.get(item.getSkuId());
		row.put("spuName", spu == null ? null : spu.getName());
		row.put("specText", sku == null ? null : joinSpecs(sku));
		row.put("contributorName", resolveContributorName(item.getUserId(), userNames));
		return row;
	}

	/**
	 * 来源成员名兜底链与成员列一致：商城昵称 → 「用户」+ID 后 6 位。
	 */
	private String resolveContributorName(String userId, Map<String, String> userNames) {
		String nickname = userNames.get(userId);
		if (StringUtils.hasText(nickname)) {
			return nickname;
		}
		if (!StringUtils.hasText(userId)) {
			return null;
		}
		return "用户" + userId.substring(Math.max(0, userId.length() - 6));
	}

	private String joinSpecs(GoodsSku sku) {
		if (sku.getSpecsArr() == null) {
			return null;
		}
		String joined = sku.getSpecsArr().stream()
				.map(GoodsSku.Specs::getSpecsValueName)
				.filter(StringUtils::hasText)
				.collect(Collectors.joining("；"));
		return StringUtils.hasText(joined) ? joined : null;
	}

}
