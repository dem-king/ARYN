package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartReuseDTO;
import com.aryn.cloud.order.api.dto.SharedCartPlanDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.support.ReplenishProgressCalculator;
import com.aryn.cloud.order.api.vo.ReplenishProgressVO;
import com.aryn.cloud.order.api.vo.SharedCartReuseVO;
import com.aryn.cloud.order.api.vo.SharedCartSummaryVO;
import com.aryn.cloud.order.api.vo.SharedCartVO;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.ISharedCartService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteShipProductProfileService;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 共享购物车服务实现。
 *
 * <p>提交时服务端重新校验数量规则（MOQ/步长）并复用订单创建服务
 * （价格、库存、船舶成员关系再次校验）；提交以购物车维度幂等。
 *
 * @author aryn
 * @since 2026/9/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SharedCartServiceImpl implements ISharedCartService {

	/** 收集有效期：24 小时，自创建时刻起算（业务规则，2026-09-20 确认） */
	private static final long COLLECT_WINDOW_HOURS = 24L;

	private final SharedCartMapper sharedCartMapper;

	private final SharedCartMemberMapper sharedCartMemberMapper;

	private final SharedCartItemMapper sharedCartItemMapper;

	private final IOrderInfoService orderInfoService;

	@DubboReference
	private RemoteShipProductProfileService remoteShipProductProfileService;

	/** 商品域：摘要卡片需要 SKU 售价与商品名（明细表只存 ID，不存快照） */
	@DubboReference
	private RemoteGoodsSkuService remoteGoodsSkuService;

	@DubboReference
	private RemoteVesselService remoteVesselService;

	@DubboReference
	private RemoteMallUserService remoteMallUserService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCart create(String tenantId, String userId, SharedCartCreateDTO dto) {
		// 同一船舶同时只允许一个「收集中」的购物车：否则同船同一靠港会各下各的单。
		// 命中时返回已有购物车，由前端引导用户加入（并发场景由 uk_shared_cart_active 兜底）。
		SharedCart existing = sharedCartMapper.selectOne(Wrappers.lambdaQuery(SharedCart.class)
				.eq(SharedCart::getTenantId, tenantId)
				.eq(SharedCart::getVesselId, dto.getVesselId())
				.eq(SharedCart::getStatus, SharedCart.STATUS_COLLECTING)
				.last("LIMIT 1"));
		if (existing != null) {
			log.info("船舶[{}]已有进行中的共享购物车[{}]，创建请求被引导至已有购物车", dto.getVesselId(), existing.getId());
			existing.setAdoptedExisting(Boolean.TRUE);
			return existing;
		}

		SharedCart cart = new SharedCart();
		cart.setId(IdWorker.getIdStr());
		cart.setCartNo("SC" + IdWorker.getIdStr());
		cart.setVesselId(dto.getVesselId());
		cart.setVesselCallId(dto.getVesselCallId());
		cart.setOwnerUserId(userId);
		cart.setConfirmerUserId(userId);
		cart.setStatus(SharedCart.STATUS_COLLECTING);
		// 有效期由服务端计算：24 小时，自创建时刻起算。
		// 历史实现直接采用前端传值，前端不传即永不过期，且客户端时钟不可信。
		cart.setExpiresAt(LocalDateTime.now().plusHours(COLLECT_WINDOW_HOURS));
		cart.setRemark(dto.getRemark());
		cart.setVersion(0);
		cart.setTenantId(tenantId);
		cart.setCreateTime(LocalDateTime.now());
		cart.setDelFlag("0");
		sharedCartMapper.insert(cart);

		SharedCartMember owner = new SharedCartMember();
		owner.setCartId(cart.getId());
		owner.setUserId(userId);
		owner.setMemberRole(SharedCartMember.ROLE_OWNER);
		owner.setCanEdit("1");
		owner.setCanConfirm("1");
		owner.setJoinedTime(LocalDateTime.now());
		owner.setTenantId(tenantId);
		owner.setCreateTime(LocalDateTime.now());
		owner.setDelFlag("0");
		sharedCartMemberMapper.insert(owner);
		return cart;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCartMember inviteMember(String tenantId, String cartId, String operatorUserId, String memberUserId,
			String memberRole) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireOwner(cart, operatorUserId);
		requireEditable(cart);
		if (!StringUtils.hasText(memberUserId)) {
			throw new ArynBusinessException("被邀请成员不能为空");
		}
		Long duplicated = sharedCartMemberMapper.selectCount(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, tenantId)
				.eq(SharedCartMember::getCartId, cartId)
				.eq(SharedCartMember::getUserId, memberUserId));
		if (duplicated != null && duplicated > 0) {
			throw new ArynBusinessException("该用户已在购物车成员中");
		}
		SharedCartMember member = new SharedCartMember();
		member.setCartId(cartId);
		member.setUserId(memberUserId);
		member.setMemberRole(SharedCartMember.ROLE_CONFIRMATOR.equals(memberRole) ? SharedCartMember.ROLE_CONFIRMATOR
				: SharedCartMember.ROLE_MEMBER);
		member.setCanEdit("1");
		member.setCanConfirm(SharedCartMember.ROLE_CONFIRMATOR.equals(memberRole) ? "1" : "0");
		member.setJoinedTime(LocalDateTime.now());
		member.setTenantId(tenantId);
		member.setCreateTime(LocalDateTime.now());
		member.setDelFlag("0");
		sharedCartMemberMapper.insert(member);
		return member;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCartItem addItem(String tenantId, String userId, String cartId, SharedCartItemDTO itemDTO) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireMembership(cart, userId);
		requireEditable(cart);
		requireNotExpired(cart);
		SharedCartItem item = new SharedCartItem();
		item.setId(IdWorker.getIdStr());
		item.setCartId(cartId);
		item.setUserId(userId);
		item.setSpuId(itemDTO.getSpuId());
		item.setSkuId(itemDTO.getSkuId());
		item.setRequestedQuantity(itemDTO.getRequestedQuantity());
		item.setMemberRemark(itemDTO.getMemberRemark());
		item.setStatus(SharedCartItem.ITEM_PENDING);
		item.setTenantId(tenantId);
		item.setCreateTime(LocalDateTime.now());
		item.setDelFlag("0");
		sharedCartItemMapper.insert(item);
		return item;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCartItem updateItem(String tenantId, String userId, String cartId, String itemId,
			SharedCartItemDTO itemDTO) {
		SharedCart cart = requireCart(tenantId, cartId);
		SharedCartItem item = requireItem(tenantId, cartId, itemId);
		requireEditable(cart);
		if (!Objects.equals(item.getUserId(), userId)) {
			throw new ArynBusinessException("只能修改自己添加的明细");
		}
		if (itemDTO.getRequestedQuantity() != null) {
			item.setRequestedQuantity(itemDTO.getRequestedQuantity());
		}
		if (itemDTO.getMemberRemark() != null) {
			item.setMemberRemark(itemDTO.getMemberRemark());
		}
		sharedCartItemMapper.updateById(item);
		return item;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCartItem updateItemPlan(String tenantId, String userId, String cartId, SharedCartPlanDTO planDTO) {
		SharedCart cart = requireCart(tenantId, cartId);
		SharedCartItem item = requireItem(tenantId, cartId, planDTO.getItemId());
		// 排计划与提交整船订单同权限：计划量决定采购目标，属确认人职责，
		// 普通成员只能报自己的需求量（见 updateItem 的"只能改自己的明细"）。
		requireEditable(cart);
		requireConfirmer(cart, userId);

		// plannedQuantity 传 null 有两种语义：显式取消计划，或"本次不动计划"。
		// 由 clearPlanned 区分，避免"只想改已采量"却把计划抹掉。
		if (Boolean.TRUE.equals(planDTO.getClearPlanned())) {
			item.setPlannedQuantity(null);
		}
		else if (planDTO.getPlannedQuantity() != null) {
			item.setPlannedQuantity(planDTO.getPlannedQuantity());
		}

		if (planDTO.getFulfilledQuantity() != null) {
			item.setFulfilledQuantity(planDTO.getFulfilledQuantity());
		}
		else if (item.getFulfilledQuantity() == null) {
			// 存量行可能为 null（加列前的老数据），补 0 让进度计算口径统一
			item.setFulfilledQuantity(0);
		}

		sharedCartItemMapper.updateById(item);
		return item;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void removeItem(String tenantId, String userId, String cartId, String itemId) {
		SharedCart cart = requireCart(tenantId, cartId);
		SharedCartItem item = requireItem(tenantId, cartId, itemId);
		requireEditable(cart);
		if (!Objects.equals(item.getUserId(), userId)) {
			throw new ArynBusinessException("只能移除自己添加的明细");
		}
		item.setStatus(SharedCartItem.ITEM_REMOVED);
		sharedCartItemMapper.updateById(item);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCartReuseVO reuseFromHistory(String tenantId, String userId, String sourceCartId,
			SharedCartReuseDTO dto) {
		SharedCart source = requireCart(tenantId, sourceCartId);
		// 权限：必须是源单成员。否则任何人都能借「复用」读取别人购物车的明细。
		requireMembership(source, userId);
		// 补给清单按船组织（船员、航线、靠港各不同），把 A 船的清单搬到 B 船没有业务意义。
		if (!Objects.equals(source.getVesselId(), dto.getVesselId())) {
			throw new ArynBusinessException("只能把历史补给单复用到同一条船上");
		}
		// 只允许复用**已结束**的单：进行中的单本身就是本轮的清单，再「复用」一次
		// 会命中同一张车、全部明细按"已存在"跳过，用户只会看到一句莫名其妙的提示。
		if (SharedCart.STATUS_DRAFT.equals(source.getStatus())
				|| SharedCart.STATUS_COLLECTING.equals(source.getStatus())
				|| SharedCart.STATUS_WAITING_CONFIRM.equals(source.getStatus())) {
			throw new ArynBusinessException("该补给单还在进行中，无需复用");
		}

		// 目标车沿用 create 的「同船同时只有一张收集中购物车」规则：命中已有车则并入。
		// 靠港计划取 dto 里**当前**这一个，而非源单那个已结束的历史靠港 ——
		// 复用出来的是本轮采购，配送窗口必须落在本次靠港。
		SharedCartCreateDTO createDTO = new SharedCartCreateDTO();
		createDTO.setVesselId(dto.getVesselId());
		createDTO.setVesselCallId(dto.getVesselCallId());
		createDTO.setRemark(StringUtils.hasText(dto.getRemark()) ? dto.getRemark() : source.getRemark());
		SharedCart target = create(tenantId, userId, createDTO);
		ensureReuseMembership(tenantId, target, userId);

		// 目标清单里已有的 SKU 不再搬：重复点「历史复用」时数量不能翻倍。
		Set<String> existingSkuIds = listItems(tenantId, target.getId()).stream()
			.map(SharedCartItem::getSkuId)
			.filter(StringUtils::hasText)
			.collect(Collectors.toSet());

		SharedCartReuseVO result = new SharedCartReuseVO();
		result.setCartId(target.getId());
		result.setCartNo(target.getCartNo());
		result.setAdoptedExisting(Boolean.TRUE.equals(target.getAdoptedExisting()));

		// 源单是「按人拆行」的，同一 SKU 可能有多行；而复用后归属只有当前操作者一人，
		// 再保留多行只会让清单变乱，因此按 SKU 合并后再落地。
		Map<String, ReuseAggregate> merged = new LinkedHashMap<>();
		Map<String, String> skipReasons = new LinkedHashMap<>();
		for (SharedCartItem item : listReusableItems(tenantId, sourceCartId)) {
			String skuId = item.getSkuId();
			if (!StringUtils.hasText(skuId)) {
				continue;
			}
			Integer quantity = reuseQuantity(item);
			if (quantity == null || quantity <= 0) {
				skipReasons.putIfAbsent(skuId, "原明细没有可用数量");
				continue;
			}
			ReuseAggregate aggregate = merged.computeIfAbsent(skuId,
					key -> new ReuseAggregate(item.getSpuId()));
			aggregate.quantity += quantity;
			// 计划量只继承**真实排过计划**的部分；源单没排计划就保持 null，
			// 不拿申请量充数 —— 与 ReplenishProgressCalculator 同一口径。
			if (item.getPlannedQuantity() != null) {
				aggregate.plannedQuantity = (aggregate.plannedQuantity == null ? 0 : aggregate.plannedQuantity)
						+ item.getPlannedQuantity();
			}
		}

		int reused = 0;
		for (Map.Entry<String, ReuseAggregate> entry : merged.entrySet()) {
			String skuId = entry.getKey();
			if (existingSkuIds.contains(skuId)) {
				skipReasons.put(skuId, "本次清单中已有该商品");
				continue;
			}
			ReuseAggregate aggregate = entry.getValue();
			SharedCartItem item = new SharedCartItem();
			item.setId(IdWorker.getIdStr());
			item.setCartId(target.getId());
			item.setUserId(userId);
			item.setSpuId(aggregate.spuId);
			item.setSkuId(skuId);
			item.setRequestedQuantity(aggregate.quantity);
			item.setPlannedQuantity(aggregate.plannedQuantity);
			// 已采量不继承：那是上一轮的既成事实，继承过来进度条一上来就是满的。
			item.setFulfilledQuantity(0);
			item.setStatus(SharedCartItem.ITEM_PENDING);
			item.setTenantId(tenantId);
			item.setCreateTime(LocalDateTime.now());
			item.setDelFlag("0");
			sharedCartItemMapper.insert(item);
			reused++;
		}

		result.setReusedCount(reused);
		result.setSkippedCount(skipReasons.size());
		skipReasons.forEach((skuId, reason) -> result.getSkipped().add(new SharedCartReuseVO.Skipped(skuId, reason)));
		log.info("复用历史补给单：source={} target={} 复用={} 跳过={}", sourceCartId, target.getId(), reused,
				skipReasons.size());
		return result;
	}

	/**
	 * 复用时确认操作者在目标车里有成员关系。
	 *
	 * <p>{@code create} 命中「同船已有收集中购物车」时只回传该车、不补成员行，
	 * 若非成员就会被写进一张自己打不开的清单（详情接口按成员校验）。
	 * 复用者既然能读同一艘船的历史单，就该能参与本轮的同一张车。
	 */
	private void ensureReuseMembership(String tenantId, SharedCart cart, String userId) {
		if (Objects.equals(cart.getOwnerUserId(), userId)) {
			return;
		}
		Long count = sharedCartMemberMapper.selectCount(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, tenantId)
				.eq(SharedCartMember::getCartId, cart.getId())
				.eq(SharedCartMember::getUserId, userId));
		if (count != null && count > 0) {
			return;
		}
		SharedCartMember member = new SharedCartMember();
		member.setCartId(cart.getId());
		member.setUserId(userId);
		member.setMemberRole(SharedCartMember.ROLE_MEMBER);
		member.setCanEdit("1");
		member.setCanConfirm("0");
		member.setJoinedTime(LocalDateTime.now());
		member.setTenantId(tenantId);
		member.setCreateTime(LocalDateTime.now());
		member.setDelFlag("0");
		sharedCartMemberMapper.insert(member);
	}

	/**
	 * 可复用的源明细：待确认 + 已确认（不含已移除）。
	 *
	 * <p>刻意不复用 {@code listItems}：它只取 {@code ITEM_PENDING}，而已提交的历史单里
	 * 被核定过数量的行已置为 {@code ITEM_CONFIRMED} —— 直接拿它会漏掉这些行，
	 * 复用出来的清单凭空少几项。
	 */
	private List<SharedCartItem> listReusableItems(String tenantId, String cartId) {
		return sharedCartItemMapper.selectList(Wrappers.lambdaQuery(SharedCartItem.class)
				.eq(SharedCartItem::getTenantId, tenantId)
				.eq(SharedCartItem::getCartId, cartId)
				.in(SharedCartItem::getStatus, SharedCartItem.ITEM_PENDING, SharedCartItem.ITEM_CONFIRMED)
				.orderByAsc(SharedCartItem::getCreateTime));
	}

	/**
	 * 复用时的数量口径：计划量 → 核定数量 → 申请数量。
	 *
	 * <p>计划量排在最前，因为它是本轮「打算采多少」的明确表述；没有计划才回落到
	 * 上一轮实际下单的核定/申请数量。三者都没有时返回 null，由调用方计入跳过。
	 */
	private Integer reuseQuantity(SharedCartItem item) {
		if (item.getPlannedQuantity() != null) {
			return item.getPlannedQuantity();
		}
		if (item.getApprovedQuantity() != null) {
			return item.getApprovedQuantity();
		}
		return item.getRequestedQuantity();
	}

	/** 复用过程中按 SKU 聚合的中间态 */
	private static final class ReuseAggregate {

		private final String spuId;

		private int quantity;

		private Integer plannedQuantity;

		private ReuseAggregate(String spuId) {
			this.spuId = spuId;
		}

	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String confirmAndCreateOrder(String tenantId, String userId, String cartId, SharedCartConfirmDTO confirmDTO) {
		SharedCart cart = requireCart(tenantId, cartId);
		if (SharedCart.STATUS_SUBMITTED.equals(cart.getStatus())) {
			// 提交幂等：重复确认直接返回已生成的订单
			return cart.getSubmitOrderId();
		}
		requireEditable(cart);
		requireNotExpired(cart);
		requireConfirmer(cart, userId);

		List<SharedCartItem> items = listItems(tenantId, cartId);
		if (items.isEmpty()) {
			throw new ArynBusinessException("共享购物车没有可提交的明细");
		}
		applyApprovedQuantities(tenantId, confirmDTO, items);
		validateQuantityRules(tenantId, items);

		CreateOrderDTO createOrderDTO = buildCreateOrder(cart, items, confirmDTO);
		var orderInfo = orderInfoService.createOrder(createOrderDTO);

		cart.setStatus(SharedCart.STATUS_SUBMITTED);
		cart.setSubmitOrderId(orderInfo.getId());
		cart.setSubmittedTime(LocalDateTime.now());
		sharedCartMapper.updateById(cart);
		log.info("共享购物车[{}]确认提交，生成订单[{}]，确认人[{}]", cartId, orderInfo.getId(), userId);
		return orderInfo.getId();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void close(String tenantId, String userId, String cartId) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireOwner(cart, userId);
		if (SharedCart.STATUS_SUBMITTED.equals(cart.getStatus())) {
			throw new ArynBusinessException("已提交的购物车不能关闭");
		}
		cart.setStatus(SharedCart.STATUS_CLOSED);
		sharedCartMapper.updateById(cart);
	}

	@Override
	public SharedCart getCartForUser(String tenantId, String userId, String cartId) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireMembership(cart, userId);
		return cart;
	}

	@Override
	public SharedCartVO getCartDetail(String tenantId, String userId, String cartId) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireMembership(cart, userId);
		return buildVOs(List.of(cart), tenantId, userId).get(0);
	}

	@Override
	public List<SharedCartVO> listMyCarts(String tenantId, String userId) {
		// 创建时已写入发起人成员行，故成员表同时覆盖「我发起的」与「我被邀请的」
		List<SharedCartMember> memberships = sharedCartMemberMapper.selectList(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, tenantId)
				.eq(SharedCartMember::getUserId, userId));
		if (memberships.isEmpty()) {
			return List.of();
		}
		List<String> cartIds = memberships.stream().map(SharedCartMember::getCartId).distinct().toList();
		List<SharedCart> carts = sharedCartMapper.selectList(Wrappers.lambdaQuery(SharedCart.class)
				.eq(SharedCart::getTenantId, tenantId)
				.in(SharedCart::getId, cartIds)
				.orderByDesc(SharedCart::getCreateTime));
		if (carts.isEmpty()) {
			return List.of();
		}
		return buildVOs(carts, tenantId, userId);
	}

	@Override
	public SharedCartSummaryVO getActiveSummary(String tenantId, String userId, String vesselCallId) {
		SharedCartSummaryVO summary = new SharedCartSummaryVO();
		SharedCart active = findActiveCart(tenantId, userId, vesselCallId);
		if (active == null) {
			// 无进行中的购物车是正常状态（首页展示空态引导创建），不作为异常
			return summary;
		}

		SharedCartVO cartVO = buildVOs(List.of(active), tenantId, userId).get(0);
		summary.setCart(cartVO);
		summary.setMemberCount(cartVO.getMemberCount());

		List<SharedCartItem> items = listItems(tenantId, active.getId());
		summary.setItemCount(items.size());
		if (items.isEmpty()) {
			return summary;
		}

		Map<String, GoodsSku> skuMap = loadSkuMap(items);
		List<SharedCartSummaryVO.SummaryItem> preview = new ArrayList<>();
		for (SharedCartItem item : items) {
			GoodsSku sku = skuMap.get(item.getSkuId());
			// 有计划的按计划量算钱（用户关心"这次要花多少"，不是"谁报了多少"）；
			// 未设计划才回落需求量，避免出现 ¥0 的假合计。
			int quantity = item.getPlannedQuantity() != null
					? item.getPlannedQuantity()
					: (item.getRequestedQuantity() == null ? 0 : item.getRequestedQuantity());
			BigDecimal unitPrice = sku == null || sku.getSalesPrice() == null
					? BigDecimal.ZERO
					: sku.getSalesPrice();
			BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(quantity));

			SharedCartSummaryVO.SummaryItem row = new SharedCartSummaryVO.SummaryItem();
			row.setItemId(item.getId());
			row.setSpuId(item.getSpuId());
			row.setSkuId(item.getSkuId());
			row.setQuantity(item.getRequestedQuantity() == null ? 0 : item.getRequestedQuantity());
			row.setAmount(amount);
			// 行级进度与整单进度用同一套计算器，避免两处口径漂移
			ReplenishProgressVO rowProgress = ReplenishProgressCalculator.ofRow(
					item.getPlannedQuantity(), item.getFulfilledQuantity());
			row.setPlannedQuantity(rowProgress.getPlannedQuantity());
			row.setFulfilledQuantity(rowProgress.getFulfilledQuantity());
			row.setRemainingQuantity(rowProgress.getRemainingQuantity());
			row.setCompleted(rowProgress.getCompleted());
			if (sku != null) {
				row.setSpuName(sku.getGoodsSpu() == null ? null : sku.getGoodsSpu().getName());
				row.setPicUrl(resolvePicUrl(sku));
				row.setSpecsInfo(joinSpecs(sku));
			}
			preview.add(row);
		}

		// 整单进度与合计：统一由计算器汇总（按项数算百分比，不按数量）
		ReplenishProgressVO.Summary progress = ReplenishProgressCalculator.summarize(items, item -> {
			GoodsSku sku = skuMap.get(item.getSkuId());
			if (sku == null || sku.getSalesPrice() == null) {
				return BigDecimal.ZERO;
			}
			int quantity = item.getPlannedQuantity() != null
					? item.getPlannedQuantity()
					: (item.getRequestedQuantity() == null ? 0 : item.getRequestedQuantity());
			return sku.getSalesPrice().multiply(BigDecimal.valueOf(quantity));
		});
		summary.setProgress(progress);
		summary.setTotalAmount(progress.getTotalAmount());

		// 预览只取前 N 条，并显式告知是否被截断，避免前端自行猜测「还差…」是否完整
		boolean truncated = preview.size() > SharedCartSummaryVO.MAX_PREVIEW_ITEMS;
		summary.setPreviewTruncated(truncated);
		summary.setPreviewItems(truncated
				? new ArrayList<>(preview.subList(0, SharedCartSummaryVO.MAX_PREVIEW_ITEMS))
				: preview);
		return summary;
	}

	/**
	 * 找当前进行中的共享购物车：我参与 + 收集中 + 未过期，按创建时间取最近一条。
	 *
	 * <p>传 {@code vesselCallId} 时优先该靠港计划（首页刷新到别的靠港不应串出上一港的清单）；
	 * 该靠港无进行中购物车时回落为不限靠港，保证卡片仍能体现"我有一条清单在收集中"。
	 */
	private SharedCart findActiveCart(String tenantId, String userId, String vesselCallId) {
		List<SharedCartMember> memberships = sharedCartMemberMapper.selectList(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, tenantId)
				.eq(SharedCartMember::getUserId, userId));
		if (memberships.isEmpty()) {
			return null;
		}
		List<String> cartIds = memberships.stream().map(SharedCartMember::getCartId).distinct().toList();

		SharedCart matched = selectActive(tenantId, cartIds, vesselCallId);
		if (matched == null && StringUtils.hasText(vesselCallId)) {
			matched = selectActive(tenantId, cartIds, null);
		}
		return matched;
	}

	private SharedCart selectActive(String tenantId, List<String> cartIds, String vesselCallId) {
		return sharedCartMapper.selectOne(Wrappers.lambdaQuery(SharedCart.class)
				.eq(SharedCart::getTenantId, tenantId)
				.in(SharedCart::getId, cartIds)
				.eq(SharedCart::getStatus, SharedCart.STATUS_COLLECTING)
				// 过期由 Job 异步置为已关闭，存在"已过期但尚未被扫到"的窗口，这里按时间再挡一次
				.and(query -> query.isNull(SharedCart::getExpiresAt)
						.or().gt(SharedCart::getExpiresAt, LocalDateTime.now()))
				.eq(StringUtils.hasText(vesselCallId), SharedCart::getVesselCallId, vesselCallId)
				.orderByDesc(SharedCart::getCreateTime)
				.last("LIMIT 1"));
	}

	/**
	 * 批量取 SKU（含商品名/规格/图片）。远程失败降级为空表：
	 * 摘要卡片少了商品名仍应能显示项数与人数，不因商品域抖动整张卡消失。
	 */
	private Map<String, GoodsSku> loadSkuMap(List<SharedCartItem> items) {
		List<String> skuIds = items.stream().map(SharedCartItem::getSkuId).distinct().toList();
		try {
			List<GoodsSku> skus = remoteGoodsSkuService.getSkuByIds(skuIds);
			if (skus == null || skus.isEmpty()) {
				return Map.of();
			}
			Map<String, GoodsSku> map = new HashMap<>();
			for (GoodsSku sku : skus) {
				map.put(sku.getId(), sku);
			}
			return map;
		}
		catch (Exception exception) {
			log.warn("共享购物车摘要补齐商品信息失败，降级为仅返回ID", exception);
			return Map.of();
		}
	}

	private String resolvePicUrl(GoodsSku sku) {
		if (StringUtils.hasText(sku.getPicUrl())) {
			return sku.getPicUrl();
		}
		GoodsSpu spu = sku.getGoodsSpu();
		if (spu != null && spu.getSpuUrls() != null && spu.getSpuUrls().length > 0) {
			return spu.getSpuUrls()[0];
		}
		return null;
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

	/**
	 * 批量组装 C 端视图：成员与明细计数各查一次后内存分组，避免逐车 N+1。
	 */
	private List<SharedCartVO> buildVOs(List<SharedCart> carts, String tenantId, String userId) {
		List<String> cartIds = carts.stream().map(SharedCart::getId).toList();

		Map<String, List<SharedCartMember>> membersByCart = sharedCartMemberMapper
			.selectList(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, tenantId)
				.in(SharedCartMember::getCartId, cartIds))
			.stream()
			.collect(Collectors.groupingBy(SharedCartMember::getCartId));

		Map<String, Long> itemCountByCart = sharedCartItemMapper
			.selectList(Wrappers.lambdaQuery(SharedCartItem.class)
				.eq(SharedCartItem::getTenantId, tenantId)
				.in(SharedCartItem::getCartId, cartIds)
				.eq(SharedCartItem::getStatus, SharedCartItem.ITEM_PENDING))
			.stream()
			.collect(Collectors.groupingBy(SharedCartItem::getCartId, Collectors.counting()));

		List<SharedCartVO> result = new ArrayList<>(carts.size());
		for (SharedCart cart : carts) {
			List<SharedCartMember> members = membersByCart.getOrDefault(cart.getId(), List.of());
			SharedCartMember viewer = members.stream()
				.filter(member -> Objects.equals(member.getUserId(), userId))
				.findFirst()
				.orElse(null);

			SharedCartVO vo = new SharedCartVO();
			vo.setId(cart.getId());
			vo.setCartNo(cart.getCartNo());
			vo.setVesselId(cart.getVesselId());
			vo.setVesselCallId(cart.getVesselCallId());
			vo.setOwnerUserId(cart.getOwnerUserId());
			vo.setConfirmerUserId(cart.getConfirmerUserId());
			vo.setStatus(cart.getStatus());
			vo.setExpiresAt(cart.getExpiresAt());
			vo.setSubmitOrderId(cart.getSubmitOrderId());
			vo.setSubmittedTime(cart.getSubmittedTime());
			vo.setRemark(cart.getRemark());
			vo.setCreateTime(cart.getCreateTime());
			vo.setMemberCount(members.size());
			vo.setItemCount(itemCountByCart.getOrDefault(cart.getId(), 0L).intValue());

			// 查看者权限：发起人天然可编辑可提交，其余按成员行标记
			boolean isOwner = Objects.equals(cart.getOwnerUserId(), userId);
			vo.setViewerIsOwner(isOwner);
			vo.setViewerRole(isOwner ? SharedCartMember.ROLE_OWNER : (viewer == null ? null : viewer.getMemberRole()));
			vo.setViewerCanEdit(isOwner || (viewer != null && "1".equals(viewer.getCanEdit())));
			vo.setViewerCanConfirm(isOwner || (viewer != null && "1".equals(viewer.getCanConfirm())));

			// 船舶/靠港展示字段：远程服务不可用或靠港已失效时降级为仅返回 ID，不影响列表可用
			if (StringUtils.hasText(cart.getVesselCallId())) {
				try {
					VesselContextDTO context = remoteVesselService.getVesselCallContext(tenantId, cart.getVesselCallId());
					if (context != null) {
						vo.setVesselName(context.getVesselName());
						vo.setPortCode(context.getPortCode());
						vo.setPortName(context.getPortName());
						vo.setBerth(context.getBerth());
						vo.setDeliveryWindowStart(context.getDeliveryWindowStart());
						vo.setDeliveryWindowEnd(context.getDeliveryWindowEnd());
					}
				}
				catch (Exception ex) {
					log.warn("共享购物车[{}]补齐船舶上下文失败，降级为仅返回ID", cart.getId(), ex);
				}
			}
			result.add(vo);
		}
		return result;
	}

	@Override
	public List<SharedCartMember> listMembers(String tenantId, String cartId) {
		return sharedCartMemberMapper.selectList(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, tenantId)
				.eq(SharedCartMember::getCartId, cartId)
				.orderByAsc(SharedCartMember::getJoinedTime));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCartMember updateMemberDisplayName(String tenantId, String userId, String cartId, String displayName) {
		if (!StringUtils.hasText(displayName)) {
			throw new ArynBusinessException("姓名不能为空");
		}
		if (displayName.trim().length() > 64) {
			throw new ArynBusinessException("姓名长度不能超过64");
		}
		SharedCart cart = requireCart(tenantId, cartId);
		requireMembership(cart, userId);
		SharedCartMember member = sharedCartMemberMapper.selectOne(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, tenantId)
				.eq(SharedCartMember::getCartId, cartId)
				.eq(SharedCartMember::getUserId, userId));
		if (member == null) {
			throw new ArynBusinessException("您不是该共享购物车成员");
		}
		member.setDisplayName(displayName.trim());
		member.setUpdateTime(LocalDateTime.now());
		sharedCartMemberMapper.updateById(member);
		return member;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String ensureShareToken(String tenantId, String userId, String cartId) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireOwner(cart, userId);
		if (StringUtils.hasText(cart.getShareToken())) {
			return cart.getShareToken();
		}
		String token = UUID.randomUUID().toString().replace("-", "");
		cart.setShareToken(token);
		cart.setUpdateTime(LocalDateTime.now());
		sharedCartMapper.updateById(cart);
		return token;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCart joinByShareToken(String tenantId, String userId, String shareToken) {
		if (!StringUtils.hasText(shareToken)) {
			throw new ArynBusinessException("分享链接无效");
		}
		SharedCart cart = sharedCartMapper.selectOne(Wrappers.lambdaQuery(SharedCart.class)
				.eq(SharedCart::getTenantId, tenantId)
				.eq(SharedCart::getShareToken, shareToken.trim())
				.last("LIMIT 1"));
		if (cart == null) {
			throw new ArynBusinessException("分享链接无效或已被撤销");
		}
		// 令牌随购物车有效期失效：已提交/已完成/已关闭或超出 24 小时的都不接受新成员
		requireEditable(cart);
		requireNotExpired(cart);

		// 已在成员表则幂等返回，避免重复插入
		Long existingMember = sharedCartMemberMapper.selectCount(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, tenantId)
				.eq(SharedCartMember::getCartId, cart.getId())
				.eq(SharedCartMember::getUserId, userId));
		if (existingMember != null && existingMember > 0) {
			return cart;
		}

		// 自动补建船舶成员关系：船员扫码进群即获得该船的船员身份，无需后台配置。
		// 失败不阻断加入——Dubbo 不可用时仍允许参与本次采购，船舶成员校验在加购/下单环节再兜底。
		boolean member = false;
		try {
			member = remoteVesselService.isVesselMember(tenantId, cart.getVesselId(), userId);
		}
		catch (Exception e) {
			log.warn("校验船舶成员关系失败，继续加入共享购物车：cartId={} userId={}", cart.getId(), userId, e);
		}
		if (!member) {
			try {
				remoteVesselService.bindMemberByShare(tenantId, cart.getVesselId(), userId);
			}
			catch (Exception e) {
				log.warn("自动绑定船舶成员失败：cartId={} userId={}", cart.getId(), userId, e);
			}
		}

		SharedCartMember newMember = new SharedCartMember();
		newMember.setCartId(cart.getId());
		newMember.setUserId(userId);
		newMember.setMemberRole(SharedCartMember.ROLE_MEMBER);
		newMember.setCanEdit("1");
		newMember.setCanConfirm("0");
		newMember.setJoinedTime(LocalDateTime.now());
		newMember.setTenantId(tenantId);
		newMember.setCreateTime(LocalDateTime.now());
		newMember.setDelFlag("0");
		sharedCartMemberMapper.insert(newMember);
		log.info("用户[{}]凭分享令牌加入共享购物车[{}]", userId, cart.getId());
		return cart;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int closeExpiredCarts(int batchSize) {
		List<SharedCart> expired = sharedCartMapper.selectList(Wrappers.lambdaQuery(SharedCart.class)
				.in(SharedCart::getStatus, SharedCart.STATUS_COLLECTING, SharedCart.STATUS_WAITING_CONFIRM)
				.isNotNull(SharedCart::getExpiresAt)
				.lt(SharedCart::getExpiresAt, LocalDateTime.now())
				.last("LIMIT " + Math.max(1, batchSize)));
		if (expired.isEmpty()) {
			return 0;
		}
		int closed = 0;
		for (SharedCart cart : expired) {
			// 条件更新保证多实例并发下幂等：仅当仍处于可关闭状态时才改
			int updated = sharedCartMapper.update(null, Wrappers.<SharedCart>lambdaUpdate()
					.eq(SharedCart::getId, cart.getId())
					.in(SharedCart::getStatus, SharedCart.STATUS_COLLECTING, SharedCart.STATUS_WAITING_CONFIRM)
					.set(SharedCart::getStatus, SharedCart.STATUS_CLOSED)
					.set(SharedCart::getUpdateTime, LocalDateTime.now()));
			closed += updated;
		}
		if (closed > 0) {
			log.info("共享购物车过期关闭：本次处理 {} 条", closed);
		}
		return closed;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public boolean archiveOnOrderSigned(String orderId) {
		if (!StringUtils.hasText(orderId)) {
			return false;
		}
		SharedCart cart = sharedCartMapper.selectOne(Wrappers.lambdaQuery(SharedCart.class)
				.eq(SharedCart::getSubmitOrderId, orderId)
				.last("LIMIT 1"));
		if (cart == null) {
			return false;
		}
		if (SharedCart.STATUS_COMPLETED.equals(cart.getStatus())) {
			return true;
		}
		int updated = sharedCartMapper.update(null, Wrappers.<SharedCart>lambdaUpdate()
				.eq(SharedCart::getId, cart.getId())
				.eq(SharedCart::getStatus, SharedCart.STATUS_SUBMITTED)
				.set(SharedCart::getStatus, SharedCart.STATUS_COMPLETED)
				.set(SharedCart::getCompletedTime, LocalDateTime.now()));
		if (updated > 0) {
			log.info("共享购物车[{}]随订单[{}]签收归档为已完成", cart.getId(), orderId);
			return true;
		}
		return false;
	}

	@Override
	public List<SharedCartItem> listItems(String tenantId, String cartId) {
		return sharedCartItemMapper.selectList(Wrappers.lambdaQuery(SharedCartItem.class)
				.eq(SharedCartItem::getTenantId, tenantId)
				.eq(SharedCartItem::getCartId, cartId)
				.eq(SharedCartItem::getStatus, SharedCartItem.ITEM_PENDING)
				.orderByAsc(SharedCartItem::getCreateTime));
	}

	// ---------------------------------------------------------------------
	// 内部方法
	// ---------------------------------------------------------------------

	private SharedCart requireCart(String tenantId, String cartId) {
		SharedCart cart = sharedCartMapper.selectOne(Wrappers.lambdaQuery(SharedCart.class)
				.eq(SharedCart::getTenantId, tenantId)
				.eq(SharedCart::getId, cartId));
		if (cart == null) {
			throw new ArynBusinessException("共享购物车不存在");
		}
		return cart;
	}

	private SharedCartItem requireItem(String tenantId, String cartId, String itemId) {
		SharedCartItem item = sharedCartItemMapper.selectOne(Wrappers.lambdaQuery(SharedCartItem.class)
				.eq(SharedCartItem::getTenantId, tenantId)
				.eq(SharedCartItem::getCartId, cartId)
				.eq(SharedCartItem::getId, itemId));
		if (item == null || SharedCartItem.ITEM_REMOVED.equals(item.getStatus())) {
			throw new ArynBusinessException("购物车明细不存在");
		}
		return item;
	}

	private void requireEditable(SharedCart cart) {
		if (SharedCart.STATUS_SUBMITTED.equals(cart.getStatus()) || SharedCart.STATUS_CLOSED.equals(cart.getStatus())) {
			throw new ArynBusinessException("购物车已提交或关闭，不能继续编辑");
		}
	}

	private void requireNotExpired(SharedCart cart) {
		if (cart.getExpiresAt() != null && cart.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new ArynBusinessException("共享购物车收集已截止");
		}
	}

	private void requireOwner(SharedCart cart, String userId) {
		if (!Objects.equals(cart.getOwnerUserId(), userId)) {
			throw new ArynBusinessException("仅发起人可以执行该操作");
		}
	}

	private void requireMembership(SharedCart cart, String userId) {
		if (!Objects.equals(cart.getOwnerUserId(), userId)) {
			Long count = sharedCartMemberMapper.selectCount(Wrappers.lambdaQuery(SharedCartMember.class)
					.eq(SharedCartMember::getTenantId, cart.getTenantId())
					.eq(SharedCartMember::getCartId, cart.getId())
					.eq(SharedCartMember::getUserId, userId));
			if (count == null || count == 0) {
				throw new ArynBusinessException("无权访问该共享购物车");
			}
		}
	}

	private void requireConfirmer(SharedCart cart, String userId) {
		if (Objects.equals(cart.getConfirmerUserId(), userId) || Objects.equals(cart.getOwnerUserId(), userId)) {
			return;
		}
		Long count = sharedCartMemberMapper.selectCount(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, cart.getTenantId())
				.eq(SharedCartMember::getCartId, cart.getId())
				.eq(SharedCartMember::getUserId, userId)
				.eq(SharedCartMember::getCanConfirm, "1"));
		if (count == null || count == 0) {
			throw new ArynBusinessException("仅确认人可以提交整船订单");
		}
	}

	private void applyApprovedQuantities(String tenantId, SharedCartConfirmDTO confirmDTO, List<SharedCartItem> items) {
		if (confirmDTO == null || confirmDTO.getApprovedQuantities() == null) {
			return;
		}
		Map<String, Integer> adjustments = new HashMap<>();
		confirmDTO.getApprovedQuantities()
			.forEach(change -> adjustments.put(change.getItemId(), change.getQuantity()));
		for (SharedCartItem item : items) {
			Integer approved = adjustments.get(item.getId());
			if (approved != null) {
				if (approved < 1) {
					throw new ArynBusinessException("核定数量必须大于0");
				}
				item.setApprovedQuantity(approved);
				item.setStatus(SharedCartItem.ITEM_CONFIRMED);
				sharedCartItemMapper.updateById(item);
			}
		}
	}

	/**
	 * 提交时按服务端船供包装资料校验数量规则：数量必须达到 MOQ 且为 step_qty 整数倍。
	 */
	private void validateQuantityRules(String tenantId, List<SharedCartItem> items) {
		List<String> skuIds = items.stream().map(SharedCartItem::getSkuId).distinct().toList();
		Map<String, ShipSkuProfile> profiles = new HashMap<>();
		for (ShipSkuProfile profile : remoteShipProductProfileService.getSkuProfiles(tenantId, skuIds)) {
			profiles.put(profile.getSkuId(), profile);
		}
		for (SharedCartItem item : items) {
			int quantity = item.getApprovedQuantity() != null ? item.getApprovedQuantity()
					: item.getRequestedQuantity();
			ShipSkuProfile profile = profiles.get(item.getSkuId());
			if (profile == null) {
				// 无船供包装资料的商品按普通商品处理，数量规则由结算服务校验
				continue;
			}
			int moq = profile.getMoq() != null ? profile.getMoq() : 1;
			int stepQty = profile.getStepQty() != null ? profile.getStepQty() : 1;
			if (quantity < moq) {
				throw new ArynBusinessException("SKU[" + item.getSkuId() + "]数量未达到最小起订量 " + moq);
			}
			if (quantity % stepQty != 0) {
				throw new ArynBusinessException("SKU[" + item.getSkuId() + "]数量必须是 " + stepQty + " 的整数倍");
			}
		}
	}

	private CreateOrderDTO buildCreateOrder(SharedCart cart, List<SharedCartItem> items,
			SharedCartConfirmDTO confirmDTO) {
		CreateOrderDTO createOrderDTO = new CreateOrderDTO();
		createOrderDTO.setUserId(cart.getConfirmerUserId() != null ? cart.getConfirmerUserId() : cart.getOwnerUserId());
		createOrderDTO.setDeliveryWay("4");
		createOrderDTO.setPurchaseScene("2");
		createOrderDTO.setVesselId(cart.getVesselId());
		createOrderDTO.setVesselCallId(cart.getVesselCallId());
		createOrderDTO.setCreateWay("2");
		createOrderDTO.setRequestId("SC" + cart.getId());
		if (confirmDTO != null) {
			createOrderDTO.setRecipientName(confirmDTO.getRecipientName());
			createOrderDTO.setRecipientPhone(confirmDTO.getRecipientPhone());
			createOrderDTO.setAgentName(confirmDTO.getAgentName());
			createOrderDTO.setAgentPhone(confirmDTO.getAgentPhone());
			createOrderDTO.setRemark(confirmDTO.getRemark());
		}
		createOrderDTO.setSkuReqList(splitByMember(items));
		return createOrderDTO;
	}

	/**
	 * 按成员拆分明细：一个共享购物车明细对应一条订单明细，不做 SKU 合并。
	 *
	 * <p>拆分粒度是配送贴标签的前提：仓库需要按「谁要的」逐人分拣并打印姓名标签。
	 * 历史实现按 SKU 聚合，导致同一 SKU 的多个成员被并成一行、
	 * contributor_user_id 用逗号拼接（varchar(32) 两人即溢出），无法区分归属。
	 *
	 * <p>同一成员对同一 SKU 多次加购的明细仍各自成行（保留其原始备注），
	 * 营销引擎侧由 buildPromotionContext 按 SKU 聚合数量保证阶梯价档位正确。
	 */
	private List<CreateOrderSkuReqDTO> splitByMember(List<SharedCartItem> items) {
		Map<String, String> memberNames = loadMemberDisplayNames(items.get(0).getCartId(), items);
		List<CreateOrderSkuReqDTO> requests = new ArrayList<>(items.size());
		for (SharedCartItem item : items) {
			int quantity = item.getApprovedQuantity() != null ? item.getApprovedQuantity()
					: item.getRequestedQuantity();
			CreateOrderSkuReqDTO skuReq = new CreateOrderSkuReqDTO();
			skuReq.setSkuId(item.getSkuId());
			skuReq.setSpuId(item.getSpuId());
			skuReq.setQuantity(quantity);
			skuReq.setContributorUserId(item.getUserId());
			skuReq.setContributorName(resolveContributorName(item.getUserId(), memberNames));
			skuReq.setMemberRemark(item.getMemberRemark());
			requests.add(skuReq);
		}
		return List.copyOf(requests);
	}

	/**
	 * 成员展示姓名映射（标签打印用）。成员表无姓名时以用户 ID 兜底，不阻断下单。
	 */
	private Map<String, String> loadMemberDisplayNames(String cartId, List<SharedCartItem> items) {
		List<String> userIds = items.stream().map(SharedCartItem::getUserId)
			.filter(StringUtils::hasText).distinct().toList();
		if (userIds.isEmpty() || !StringUtils.hasText(cartId)
				|| !StringUtils.hasText(ArynTenantContextHolder.getTenantId())) {
			return Map.of();
		}
		try {
			List<SharedCartMember> members = sharedCartMemberMapper.selectList(
					Wrappers.lambdaQuery(SharedCartMember.class)
						.eq(SharedCartMember::getCartId, cartId)
						.in(SharedCartMember::getUserId, userIds));
			Map<String, String> names = new HashMap<>(userIds.size());
			for (SharedCartMember member : members) {
				if (StringUtils.hasText(member.getDisplayName())) {
					names.putIfAbsent(member.getUserId(), member.getDisplayName().trim());
				}
			}
			return names;
		}
		catch (Exception ex) {
			log.warn("加载共享购物车成员姓名失败，标签姓名降级为兜底值, userIds={}", userIds, ex);
			return Map.of();
		}
	}

	/**
	 * 姓名兜底链：成员填写姓名 → 商城昵称 → 「用户」+ID 后 6 位。
	 */
	private String resolveContributorName(String userId, Map<String, String> memberNames) {
		String name = memberNames.get(userId);
		if (StringUtils.hasText(name)) {
			return name;
		}
		if (!StringUtils.hasText(userId)) {
			return null;
		}
		try {
			UserInfoVO user = remoteMallUserService.getUserById(userId);
			if (user != null && StringUtils.hasText(user.getNickname())) {
				return user.getNickname();
			}
		}
		catch (Exception ex) {
			log.warn("回填贡献者昵称失败，使用 ID 兜底, userId={}", userId, ex);
		}
		return "用户" + userId.substring(Math.max(0, userId.length() - 6));
	}

}
