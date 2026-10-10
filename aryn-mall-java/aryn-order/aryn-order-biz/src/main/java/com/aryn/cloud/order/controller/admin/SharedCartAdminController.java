package com.aryn.cloud.order.controller.admin;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.hutool.core.bean.BeanUtil;
import com.aryn.cloud.common.core.util.Result;
import com.aryn.cloud.common.log.annotation.SysLog;
import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.order.api.dto.SharedCartMemberPermissionDTO;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.ISharedCartService;
import com.aryn.cloud.order.api.entity.OrderInfo;
import com.aryn.cloud.order.mapper.OrderInfoMapper;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
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

	private final OrderInfoMapper orderInfoMapper;

	/**
	 * 成员权限开关走服务层：{@code can_edit} 的生效口径（谁能被收回、发起人例外）
	 * 由服务层集中判定，另有一个 C 端守卫 requireCanEdit 与之配对，
	 * 管理端直接改表会让两处口径漂移。
	 */
	private final ISharedCartService sharedCartService;

	@DubboReference
	private RemoteGoodsSkuService remoteGoodsSkuService;

	@DubboReference
	private RemoteGoodsSpuService remoteGoodsSpuService;

	@DubboReference
	private RemoteMallUserService remoteMallUserService;

	/**
	 * 靠港展示快照：列表要显示「哪条船 · 哪个港口」，而 shared_cart 只存靠港 ID。
	 * 用展示快照而非可下单查询——历史单（已提交/已完成）的靠港必然已结束，
	 * 走可下单查询会返回 null，列表就只剩一串雪花 ID。
	 */
	@DubboReference
	private RemoteVesselService remoteVesselService;

	@Operation(summary = "共享购物车分页")
	@SaCheckPermission("sharedcart:page")
	@GetMapping("/page")
	public Result<IPage<Map<String, Object>>> page(Page<SharedCart> page, SharedCart query) {
		String tenantId = ArynTenantContextHolder.getTenantId();
		IPage<SharedCart> cartPage = sharedCartMapper.selectPage(page, Wrappers.lambdaQuery(SharedCart.class)
				.eq(SharedCart::getTenantId, tenantId)
				.eq(StringUtils.hasText(query.getStatus()), SharedCart::getStatus, query.getStatus())
				.eq(StringUtils.hasText(query.getVesselCallId()), SharedCart::getVesselCallId,
						query.getVesselCallId())
				.like(StringUtils.hasText(query.getCartNo()), SharedCart::getCartNo, query.getCartNo())
				.orderByDesc(SharedCart::getCreateTime));

		List<SharedCart> carts = cartPage.getRecords();
		if (carts.isEmpty()) {
			// 空页直接返回：三类回填都要先收集 ID，空列表下不必发起任何查询
			return Result.success(cartPage.convert(cart -> new LinkedHashMap<>()));
		}
		// 三类回填各批量取一次：一页 10 条若逐行调用，远程与 SQL 都会被放大 10 倍
		Map<String, String> ownerNames = resolveOwnerNames(carts, tenantId);
		Map<String, String> vesselCalls = loadVesselCallSnapshots(carts, tenantId);
		Map<String, String> orderNos = loadOrderNos(carts);
		return Result.success(cartPage.convert(cart -> toListRow(cart, ownerNames, vesselCalls, orderNos)));
	}

	/**
	 * 发起人展示名：与成员页、明细来源成员、C 端同一条兜底链
	 * （成员自填姓名 → 商城昵称 → 「用户」+ID 后 6 位）。
	 *
	 * <p>发起人自然是该车的发起人成员行，其 display_name 就是自填姓名——
	 * 只读昵称会让列表显示「176****2320」而成员页显示「张伟」，同一个人对不上。
	 */
	private Map<String, String> resolveOwnerNames(List<SharedCart> carts, String tenantId) {
		List<String> cartIds = carts.stream().map(SharedCart::getId).toList();
		List<SharedCartMember> owners = sharedCartMemberMapper.selectList(
				Wrappers.lambdaQuery(SharedCartMember.class)
						.eq(SharedCartMember::getTenantId, tenantId)
						.in(SharedCartMember::getCartId, cartIds)
						.eq(SharedCartMember::getMemberRole, SharedCartMember.ROLE_OWNER));
		Map<String, String> filledNames = new HashMap<>();
		for (SharedCartMember owner : owners) {
			if (StringUtils.hasText(owner.getDisplayName())) {
				filledNames.put(owner.getCartId(), owner.getDisplayName());
			}
		}
		Map<String, String> nicknames = loadUserNames(
				carts.stream().map(SharedCart::getOwnerUserId).toList());
		Map<String, String> names = new HashMap<>();
		for (SharedCart cart : carts) {
			names.put(cart.getId(), resolveDisplayName(cart.getOwnerUserId(),
					filledNames.get(cart.getId()), nicknames));
		}
		return names;
	}

	/**
	 * 列表行组装：实体字段平铺后补船名/港口、发起人昵称、订单号。
	 *
	 * <p>与详情接口同一口径——管理端要回答「这是哪条船、谁发起的、生成哪张单」，
	 * 裸露 vesselCallId/ownerUserId/submitOrderId 三个雪花 ID 运营看不懂（2026-10-09）。
	 */
	private Map<String, Object> toListRow(SharedCart cart, Map<String, String> ownerNames,
			Map<String, String> vesselCalls, Map<String, String> orderNos) {
		Map<String, Object> row = new LinkedHashMap<>(BeanUtil.beanToMap(cart));
		// shareToken 是加入凭证，管理端列表没有任何用处，不随列表下发
		row.remove("shareToken");
		row.put("vesselCallText", vesselCalls.get(cart.getVesselCallId()));
		row.put("ownerName", ownerNames.get(cart.getId()));
		row.put("submitOrderNo", orderNos.get(cart.getSubmitOrderId()));
		return row;
	}

	/**
	 * 靠港展示快照（船名 · 港口）：与 C 端列表同源，历史单的靠港已结束也照样有名字。
	 * 远程失败降级为空表，那一列回落展示靠港 ID。
	 */
	private Map<String, String> loadVesselCallSnapshots(List<SharedCart> carts, String tenantId) {
		List<String> callIds = carts.stream().map(SharedCart::getVesselCallId).filter(StringUtils::hasText)
				.distinct().toList();
		Map<String, String> snapshot = new HashMap<>();
		for (String callId : callIds) {
			try {
				VesselContextDTO context = remoteVesselService.getVesselCallSnapshot(tenantId, callId);
				if (context != null) {
					snapshot.put(callId, joinVesselCallText(context));
				}
			}
			catch (Exception exception) {
				log.warn("共享购物车列表补齐靠港上下文失败，降级为仅返回ID, callId={}", callId, exception);
			}
		}
		return snapshot;
	}

	/** 展示口径与订单详情一致：船名 · 港口 泊位；三者都缺时返回 null */
	private String joinVesselCallText(VesselContextDTO context) {
		String location = Stream.of(context.getPortName(), context.getBerth())
				.filter(StringUtils::hasText)
				.collect(Collectors.joining(" "));
		String vesselName = context.getVesselName();
		if (!StringUtils.hasText(vesselName)) {
			return StringUtils.hasText(location) ? location : null;
		}
		return StringUtils.hasText(location) ? vesselName + " · " + location : vesselName;
	}

	/**
	 * 生成订单的订单号（不是订单主键 ID）：运营在订单模块按订单号检索，
	 * 列表下发 submitOrderId 等于给一个搜不到的值。订单一经提交不会改号。
	 */
	private Map<String, String> loadOrderNos(List<SharedCart> carts) {
		List<String> orderIds = carts.stream().map(SharedCart::getSubmitOrderId).filter(StringUtils::hasText)
				.distinct().toList();
		if (orderIds.isEmpty()) {
			return Map.of();
		}
		List<OrderInfo> orders = orderInfoMapper.selectByIds(orderIds);
		Map<String, String> orderNos = new HashMap<>();
		for (OrderInfo order : orders) {
			if (order != null && StringUtils.hasText(order.getOrderNo())) {
				orderNos.put(order.getId(), order.getOrderNo());
			}
		}
		return orderNos;
	}

	/**
	 * 批量取用户昵称。远程失败降级为空表：名称缺失时回落「用户+ID 后 6 位」。
	 */
	private Map<String, String> loadUserNames(List<String> userIds) {
		List<String> distinctIds = userIds.stream().filter(StringUtils::hasText).distinct().toList();
		if (distinctIds.isEmpty()) {
			return Map.of();
		}
		try {
			List<UserInfoVO> users = remoteMallUserService.getUserByIds(distinctIds);
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
			log.warn("共享购物车列表补齐用户昵称失败，降级为 ID 兜底", exception);
			return Map.of();
		}
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

		// 成员自填姓名（加入时填写，配送贴标签用）优先于商城昵称：先按车取出来，
		// 再统一算展示名覆写成员行——成员页与明细「来源成员」必须是同一个称呼
		Map<String, String> filledNames = new HashMap<>();
		for (SharedCartMember member : members) {
			if (StringUtils.hasText(member.getDisplayName())) {
				filledNames.put(member.getUserId(), member.getDisplayName());
			}
		}
		Map<String, String> nicknames = loadUserNames(members, items);
		members.forEach(member -> member.setDisplayName(
				resolveDisplayName(member.getUserId(), filledNames.get(member.getUserId()), nicknames)));

		Map<String, GoodsSku> skuMap = loadSkuMap(items);
		Map<String, GoodsSpu> spuMap = loadSpuMap(items);
		List<Map<String, Object>> itemRows = items.stream()
				.map(item -> toItemRow(item, skuMap, spuMap, filledNames, nicknames))
				.toList();

		Map<String, Object> detail = new HashMap<>();
		detail.put("cart", cart);
		detail.put("members", members);
		detail.put("items", itemRows);
		return Result.success(detail);
	}

	@Operation(summary = "设置成员明细维护权限（收回后该成员只能查看清单）")
	@SysLog("设置共享购物车成员明细权限")
	@SaCheckPermission("sharedcart:member:permission")
	@PutMapping("/{id}/members/{memberId}/permission")
	public Result<SharedCartMember> updateMemberPermission(@PathVariable String id, @PathVariable String memberId,
			@Valid @RequestBody SharedCartMemberPermissionDTO dto) {
		return Result.success(sharedCartService.updateMemberCanEdit(ArynTenantContextHolder.getTenantId(), id,
				memberId, dto.getCanEdit()));
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
	 * 展示名统一兜底链：**成员自填姓名 → 商城昵称 → 「用户」+ID 后 6 位**。
	 *
	 * <p>三处（列表发起人、成员页、明细来源成员）必须共用这一条链，否则同一个人
	 * 在不同视图里会显示成三个名字——成员页读自填姓名（张伟），列表与明细读昵称
	 * （掩码手机号 176****2320），运营根本无法把两边对上（2026-10-09 实测的缺陷）。
	 * 自填姓名是船员加入时专门为「配送贴标签」填的，也是 C 端详情的口径，优先级最高。
	 */
	private String resolveDisplayName(String userId, String filledName, Map<String, String> nicknames) {
		if (StringUtils.hasText(filledName)) {
			return filledName;
		}
		String nickname = nicknames.get(userId);
		if (StringUtils.hasText(nickname)) {
			return nickname;
		}
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
			Map<String, GoodsSpu> spuMap, Map<String, String> filledNames, Map<String, String> nicknames) {
		Map<String, Object> row = new LinkedHashMap<>(BeanUtil.beanToMap(item));
		GoodsSpu spu = spuMap.get(item.getSpuId());
		GoodsSku sku = skuMap.get(item.getSkuId());
		row.put("spuName", spu == null ? null : spu.getName());
		row.put("specText", sku == null ? null : joinSpecs(sku));
		// 与成员页同一兜底链：同一个人的称呼必须一致，否则运营无法把明细对到成员
		String userId = item.getUserId();
		row.put("contributorName", resolveDisplayName(userId, filledNames.get(userId), nicknames));
		return row;
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
