package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.dto.SharedCartImportConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartReuseDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartImport;
import com.aryn.cloud.order.api.entity.SharedCartImportRow;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartImportRowVO;
import com.aryn.cloud.order.api.vo.SharedCartImportVO;
import com.aryn.cloud.order.api.vo.SharedCartItemVO;
import com.aryn.cloud.order.api.vo.SharedCartReuseVO;
import com.aryn.cloud.order.api.vo.SharedCartSummaryVO;
import com.aryn.cloud.order.api.vo.SharedCartVO;
import com.aryn.cloud.order.mapper.SharedCartImportMapper;
import com.aryn.cloud.order.mapper.SharedCartImportRowMapper;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.ISharedCartService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.order.support.ChainOrderTextParser;
import com.aryn.cloud.order.support.ReplenishImportClassifier;
import com.aryn.cloud.order.support.ReplenishImportExcel;
import com.aryn.cloud.product.api.dto.ReplenishImportMatchDTO;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService;
import com.aryn.cloud.product.api.support.ReplenishMatchRules;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
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

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

	private final SharedCartImportMapper sharedCartImportMapper;

	private final SharedCartImportRowMapper sharedCartImportRowMapper;

	private final IOrderInfoService orderInfoService;

	/** 商品域：摘要卡片需要 SKU 售价与规格（明细表只存 ID，不存快照） */
	@DubboReference
	private RemoteGoodsSkuService remoteGoodsSkuService;

	/**
	 * 商品域：按 spuId 批量回填商品名。
	 *
	 * <p>不能走 {@code sku.getGoodsSpu()}：{@code getSkuByIds} 的 resultMap 不回填该关联，
	 * 名称会恒为 null，卡片只能显示 SKU ID（正常购物车链路用的 {@code getBySkuIds} 才回填）。
	 */
	@DubboReference
	private RemoteGoodsSpuService remoteGoodsSpuService;

	/** 商品域：补给单导入的编码/品名匹配（含下架商品，报告要如实给出原因） */
	@DubboReference
	private RemoteReplenishImportMatchService remoteReplenishImportMatchService;

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
		requireEditable(cart);
		requireNotExpired(cart);
		// 成员身份与明细写入权一次判完（非成员报「无权访问」，只读成员报缺编辑权）
		requireCanEdit(cart, userId);
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
		// 归属先判：不是自己的行直接说清，不必再解释编辑权。
		// 是自己的行才轮到 can_edit —— 两句话对应两种不同的"不能改"。
		if (!Objects.equals(item.getUserId(), userId)) {
			throw new ArynBusinessException("只能修改自己添加的明细");
		}
		requireCanEdit(cart, userId);
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
	public void removeItem(String tenantId, String userId, String cartId, String itemId) {
		SharedCart cart = requireCart(tenantId, cartId);
		SharedCartItem item = requireItem(tenantId, cartId, itemId);
		requireEditable(cart);
		if (!Objects.equals(item.getUserId(), userId)) {
			throw new ArynBusinessException("只能移除自己添加的明细");
		}
		requireCanEdit(cart, userId);
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
		// 复用写入的是**目标车**里、归属操作者自己的明细：先确认他在目标车里有写入权
		// （非成员则补一行可编辑的成员关系），被设为只读的成员不能靠这条侧门添行。
		requireReuseEditable(tenantId, target, userId);

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
	 * 复用时确认操作者在目标车里可以维护自己的明细；非成员则补一行可编辑的成员关系。
	 *
	 * <p>{@code create} 命中「同船已有收集中购物车」时只回传该车、不补成员行，
	 * 若非成员就会被写进一张自己打不开的清单（详情接口按成员校验）。
	 * 复用者既然能读同一艘船的历史单，就该能参与本轮的同一张车。
	 *
	 * <p>但「补成员」只适用于原本就不是成员的人：已经是成员、只是被设为只读
	 * （{@code can_edit=0}）的，复用就是他绕过只读限制给自己添行的侧门，
	 * 必须在这里挡住（与 {@code addItem} 同一道门槛）。
	 */
	private void requireReuseEditable(String tenantId, SharedCart cart, String userId) {
		if (Objects.equals(cart.getOwnerUserId(), userId)) {
			return;
		}
		SharedCartMember existing = sharedCartMemberMapper.selectOne(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, tenantId)
				.eq(SharedCartMember::getCartId, cart.getId())
				.eq(SharedCartMember::getUserId, userId));
		if (existing != null) {
			if (!"1".equals(existing.getCanEdit())) {
				throw new ArynBusinessException("你没有维护该购物车明细的权限");
			}
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
	 * 复用时的数量口径：核定数量 → 申请数量。
	 *
	 * <p>核定数量优先，因为它是上一轮确认人最终拍板的采购量；没有核定过（收集一半
	 * 就关掉的单）才回落到成员申请量。返回 0 表示上一轮把该行核定为「本次不采」，
	 * 由调用方跳过。
	 */
	private Integer reuseQuantity(SharedCartItem item) {
		if (item.getApprovedQuantity() != null) {
			return item.getApprovedQuantity();
		}
		return item.getRequestedQuantity();
	}

	/** 复用过程中按 SKU 聚合的中间态 */
	private static final class ReuseAggregate {

		private final String spuId;

		private int quantity;

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
		ensureOrderableCall(tenantId, cart);

		List<SharedCartItem> items = listItems(tenantId, cartId);
		if (items.isEmpty()) {
			throw new ArynBusinessException("共享购物车没有可提交的明细");
		}
		applyApprovedQuantities(tenantId, confirmDTO, items);

		// 全部行都被核定为「本次不采」时，下单会因没有商品明细而失败，
		// 报错还得让人看懂是「你把每一样都排除了」，而不是系统故障。
		List<CreateOrderSkuReqDTO> skuReqList = splitByMember(items);
		if (skuReqList.isEmpty()) {
			throw new ArynBusinessException("所有明细都被标记为本次不采，请至少保留一项后再提交");
		}

		CreateOrderDTO createOrderDTO = buildCreateOrder(cart, skuReqList, confirmDTO);
		var orderInfo = orderInfoService.createOrder(createOrderDTO);

		cart.setStatus(SharedCart.STATUS_SUBMITTED);
		cart.setSubmitOrderId(orderInfo.getId());
		cart.setSubmittedTime(LocalDateTime.now());
		sharedCartMapper.updateById(cart);
		log.info("共享购物车[{}]确认提交，生成订单[{}]，确认人[{}]", cartId, orderInfo.getId(), userId);
		return orderInfo.getId();
	}

	/**
	 * 提交前顺延整车的靠港归属。
	 *
	 * <p>整车的 vessel_call_id 是创建时刻的快照，而收集中会持续数天，靠港 ETD 过点后
	 * 快照即失效——下单校验会以「靠港计划不可用（不存在或已离港）」拒绝整车提交，
	 * 且共享车没有切换靠港的出路，全员明细被扣死（成员端查找有回退、加购照常，
	 * 问题直到提交才暴露，更加隐蔽）。这里与个人购物车的顺延口径一致：
	 * 快照不可用（含创建时未挂上靠港）时自动改挂该船当前可用靠港（ETA 最早一班），
	 * 随 {@code sharedCartMapper.updateById(cart)} 一起落库；没有可用靠港时给出
	 * 可读报错引导先申报靠港，而不是让用户面对底层校验的模糊文案。
	 */
	private void ensureOrderableCall(String tenantId, SharedCart cart) {
		if (StringUtils.hasText(cart.getVesselCallId())
				&& remoteVesselService.getVesselCallContext(tenantId, cart.getVesselCallId()) != null) {
			return;
		}
		VesselContextDTO available = remoteVesselService.resolveAvailableCall(tenantId, cart.getVesselId());
		if (available == null) {
			throw new ArynBusinessException("该船暂无可用靠港计划，请先申报靠港后再提交整船订单");
		}
		log.info("共享购物车[{}]的靠港[{}]已失效，提交时顺延到可用靠港[{}]",
				cart.getId(), cart.getVesselCallId(), available.getVesselCallId());
		cart.setVesselCallId(available.getVesselCallId());
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
		// 商品名单独按 SPU 批量取：SKU 查询的 resultMap 不回填 goodsSpu（见字段注释）
		Map<String, GoodsSpu> spuMap = loadSpuMap(items);
		List<SharedCartSummaryVO.SummaryItem> preview = new ArrayList<>();
		BigDecimal totalAmount = BigDecimal.ZERO;
		for (SharedCartItem item : items) {
			GoodsSku sku = skuMap.get(item.getSkuId());
			// 预估金额按成员申请量算：收集阶段还没有核定数量，
			// 申请量就是当前已知的"这次要买多少"。促销价下单时才计算，
			// 因此这里只是量级参考，前端文案用「预估」而非「合计」。
			int quantity = item.getRequestedQuantity() == null ? 0 : item.getRequestedQuantity();
			BigDecimal unitPrice = sku == null || sku.getSalesPrice() == null
					? BigDecimal.ZERO
					: sku.getSalesPrice();
			BigDecimal amount = unitPrice.multiply(BigDecimal.valueOf(quantity));
			totalAmount = totalAmount.add(amount);

			SharedCartSummaryVO.SummaryItem row = new SharedCartSummaryVO.SummaryItem();
			row.setItemId(item.getId());
			row.setSpuId(item.getSpuId());
			row.setSkuId(item.getSkuId());
			row.setQuantity(quantity);
			row.setAmount(amount);
			// 名称来自 SPU 表；图片优先 SKU 图，缺省回落 SPU 主图（sku 可能因下架查不到）
			GoodsSpu spu = spuMap.get(item.getSpuId());
			row.setSpuName(spu == null ? null : spu.getName());
			if (sku != null) {
				row.setPicUrl(resolvePicUrl(sku, spu));
				row.setSpecsInfo(joinSpecs(sku));
			}
			else if (spu != null) {
				row.setPicUrl(resolvePicUrl(null, spu));
			}
			preview.add(row);
		}
		summary.setTotalAmount(totalAmount);

		// 预览只取前 N 条，并显式告知是否被截断，避免前端自行猜测预览是否完整
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

	/**
	 * 批量取 SPU（商品名/主图）。远程失败降级为空表：
	 * 名称缺失时前端回落到 SKU ID，卡片其余信息照常展示。
	 */
	private Map<String, GoodsSpu> loadSpuMap(List<SharedCartItem> items) {
		List<String> spuIds = items.stream().map(SharedCartItem::getSpuId).filter(StringUtils::hasText).distinct()
			.toList();
		if (spuIds.isEmpty()) {
			return Map.of();
		}
		try {
			List<GoodsSpu> spus = remoteGoodsSpuService.getSpuByIds(spuIds);
			if (spus == null || spus.isEmpty()) {
				return Map.of();
			}
			Map<String, GoodsSpu> map = new HashMap<>();
			for (GoodsSpu spu : spus) {
				map.put(spu.getId(), spu);
			}
			return map;
		}
		catch (Exception exception) {
			log.warn("共享购物车摘要补齐商品名称失败，降级为仅返回ID", exception);
			return Map.of();
		}
	}

	/**
	 * 行图片：SKU 图 → SPU 主图首张。
	 *
	 * <p>{@code spu_urls} 存量数据存在字面量 {@code "[]"}（JsonArrayStringTypeHandler
	 * 会解析成单元素 {@code ["[]"]}），作图地址时须跳过。
	 */
	private String resolvePicUrl(GoodsSku sku, GoodsSpu spu) {
		if (sku != null && StringUtils.hasText(sku.getPicUrl())) {
			return sku.getPicUrl();
		}
		if (spu == null || spu.getSpuUrls() == null) {
			return null;
		}
		for (String url : spu.getSpuUrls()) {
			if (StringUtils.hasText(url) && !"[]".equals(url.trim())) {
				return url;
			}
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
	 * 批量取靠港展示快照（含已结束的靠港）。
	 *
	 * <p>用展示快照而非可下单查询：历史单的船名/港口必须照常显示，靠港结束不该让卡片
	 * 退化成「船舶信息加载中」。是否仍可下单由 {@code callOrderable} 标记单独下发。
	 *
	 * <p>按 vesselCallId 去重：同一航次的多次采购挂在同一个靠港上，一次列表只查一次。
	 * 远程失败降级为空表——列表仍要能打开，缺失的那张卡退回占位文案而不是整页报错。
	 */
	private Map<String, VesselContextDTO> loadVesselCallSnapshots(List<SharedCart> carts, String tenantId) {
		List<String> callIds = carts.stream()
			.map(SharedCart::getVesselCallId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList();
		if (callIds.isEmpty()) {
			return Map.of();
		}
		Map<String, VesselContextDTO> snapshots = new HashMap<>();
		for (String callId : callIds) {
			try {
				VesselContextDTO context = remoteVesselService.getVesselCallSnapshot(tenantId, callId);
				if (context != null) {
					snapshots.put(callId, context);
				}
			}
			catch (Exception exception) {
				log.warn("靠港[{}]展示快照获取失败，相关卡片降级为仅返回ID", callId, exception);
			}
		}
		return snapshots;
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

		// 明细一次查全（未移除）：列表卡片要计数与进度。只按 ITEM_PENDING 统计会让
		// 已提交的单显示「0 项商品」—— 提交时行已置为 ITEM_CONFIRMED。
		Map<String, List<SharedCartItem>> itemsByCart = sharedCartItemMapper
			.selectList(Wrappers.lambdaQuery(SharedCartItem.class)
				.eq(SharedCartItem::getTenantId, tenantId)
				.in(SharedCartItem::getCartId, cartIds)
				.ne(SharedCartItem::getStatus, SharedCartItem.ITEM_REMOVED))
			.stream()
			.collect(Collectors.groupingBy(SharedCartItem::getCartId));

		Map<String, VesselContextDTO> snapshotsByCall = loadVesselCallSnapshots(carts, tenantId);

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
			List<SharedCartItem> cartItems = itemsByCart.getOrDefault(cart.getId(), List.of());
			vo.setItemCount(cartItems.size());

			// 查看者权限：发起人天然可编辑可提交，其余按成员行标记
			boolean isOwner = Objects.equals(cart.getOwnerUserId(), userId);
			vo.setViewerIsOwner(isOwner);
			vo.setViewerRole(isOwner ? SharedCartMember.ROLE_OWNER : (viewer == null ? null : viewer.getMemberRole()));
			vo.setViewerCanEdit(isOwner || (viewer != null && "1".equals(viewer.getCanEdit())));
			vo.setViewerCanConfirm(isOwner || (viewer != null && "1".equals(viewer.getCanConfirm())));

			// 船舶/靠港展示字段取自快照：靠港已结束的历史单照样有船名与港口。
			// 快照缺失（靠港记录不存在或远程不可用）时才降级为仅返回 ID。
			VesselContextDTO context = StringUtils.hasText(cart.getVesselCallId())
				? snapshotsByCall.get(cart.getVesselCallId())
				: null;
			if (context != null) {
				vo.setVesselName(context.getVesselName());
				vo.setPortCode(context.getPortCode());
				vo.setPortName(context.getPortName());
				vo.setBerth(context.getBerth());
				vo.setDeliveryWindowStart(context.getDeliveryWindowStart());
				vo.setDeliveryWindowEnd(context.getDeliveryWindowEnd());
				vo.setCallOrderable(context.getCallOrderable());
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
	public SharedCartMember updateMemberCanEdit(String tenantId, String cartId, String memberId, String canEdit) {
		SharedCart cart = requireCart(tenantId, cartId);
		SharedCartMember member = sharedCartMemberMapper.selectOne(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, tenantId)
				.eq(SharedCartMember::getCartId, cartId)
				.eq(SharedCartMember::getId, memberId));
		if (member == null) {
			throw new ArynBusinessException("成员不存在");
		}
		// 发起人在 requireCanEdit 里天然放行（他创建车时就承担了维护职责），
		// 写这个标记不会生效——拒绝比静默无效好：运营看到"已保存"却依旧能加购，
		// 只会怀疑开关坏了。
		if (Objects.equals(member.getUserId(), cart.getOwnerUserId())) {
			throw new ArynBusinessException("发起人始终可维护明细，无需设置");
		}
		member.setCanEdit("1".equals(canEdit) ? "1" : "0");
		member.setUpdateTime(LocalDateTime.now());
		sharedCartMemberMapper.updateById(member);
		log.info("管理端设置共享购物车[{}]成员[{}]明细权限为 can_edit={}", cartId, member.getUserId(),
				member.getCanEdit());
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

	@Override
	public List<SharedCartItemVO> listItemVOs(String tenantId, String cartId) {
		// 刻意不复用 listItems：它只取 ITEM_PENDING，服务于提交/加购等写入路径；
		// 而明细的**展示**必须覆盖已提交的单 —— 提交时行会被置为 ITEM_CONFIRMED，
		// 只查待确认会让「已提交」的车看起来一条明细都没有（列表卡片早已按同一口径修正）。
		List<SharedCartItem> items = sharedCartItemMapper.selectList(Wrappers.lambdaQuery(SharedCartItem.class)
				.eq(SharedCartItem::getTenantId, tenantId)
				.eq(SharedCartItem::getCartId, cartId)
				.ne(SharedCartItem::getStatus, SharedCartItem.ITEM_REMOVED)
				.orderByAsc(SharedCartItem::getCreateTime));
		if (items.isEmpty()) {
			return List.of();
		}
		Map<String, GoodsSku> skuMap = loadSkuMap(items);
		Map<String, GoodsSpu> spuMap = loadSpuMap(items);

		List<SharedCartItemVO> result = new ArrayList<>(items.size());
		for (SharedCartItem item : items) {
			GoodsSku sku = skuMap.get(item.getSkuId());
			GoodsSpu spu = spuMap.get(item.getSpuId());

			SharedCartItemVO vo = new SharedCartItemVO();
			vo.setId(item.getId());
			vo.setCartId(item.getCartId());
			vo.setUserId(item.getUserId());
			vo.setAttributedName(item.getAttributedName());
			vo.setSpuId(item.getSpuId());
			vo.setSkuId(item.getSkuId());
			vo.setSpuName(spu == null ? null : spu.getName());
			vo.setSpecsInfo(sku == null ? null : joinSpecs(sku));
			vo.setPicUrl(resolvePicUrl(sku, spu));
			vo.setRequestedQuantity(item.getRequestedQuantity());
			vo.setApprovedQuantity(item.getApprovedQuantity());
			vo.setMemberRemark(item.getMemberRemark());
			vo.setStatus(item.getStatus());
			vo.setCreateTime(item.getCreateTime());

			// 单价缺失（商品已下架/被删、商品域抖动）时留 null 而不是 0：
			// 用 0 冒充已定价会把「没算到的钱」藏起来，用户以为整单就这么多。
			// 行小计不在这里下发：单价与数量都已给出，金额由前端按同一份口径
			// 现算（还要跟随确认人改核定数量实时变化），多一个后端快照只会让
			// 弹层里的数字和列表对不上。
			vo.setUnitPrice(sku == null ? null : sku.getSalesPrice());
			result.add(vo);
		}
		return result;
	}

	/** 明细行的计价数量：收集阶段没有核定数量，一律按成员申请量算 */
	private int quantityOf(SharedCartItem item) {
		return item.getRequestedQuantity() == null ? 0 : item.getRequestedQuantity();
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

	/**
	 * 明细写入权：往清单里写「自己的行」必须 {@code can_edit=1}。
	 *
	 * <p>这个标记同时被 {@code buildVOs} 下发成 {@code viewerCanEdit}，C 端据此
	 * 隐藏加购入口。校验必须在这里也做一遍：否则前端隐藏入口、服务端照单全收，
	 * 同一个成员会被两套事实描述（界面上没有共享车可选，直接调接口却能写进去）。
	 *
	 * <p>发起人天然可编辑，不查成员行（他创建车时就承担了维护职责）。
	 * 与 {@code can_confirm} 分开：确认人可核定数量、提交整船订单，
	 * 不代表可以往清单里加自己的货——两者是不同职责，见 {@code requireConfirmer}。
	 */
	private void requireCanEdit(SharedCart cart, String userId) {
		if (Objects.equals(cart.getOwnerUserId(), userId)) {
			return;
		}
		SharedCartMember member = sharedCartMemberMapper.selectOne(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, cart.getTenantId())
				.eq(SharedCartMember::getCartId, cart.getId())
				.eq(SharedCartMember::getUserId, userId));
		if (member == null) {
			throw new ArynBusinessException("无权访问该共享购物车");
		}
		if (!"1".equals(member.getCanEdit())) {
			throw new ArynBusinessException("你没有维护该购物车明细的权限");
		}
	}

	/**
	 * 写入确认人的核定数量：0 表示「本次不采」，该行不进整船订单。
	 *
	 * <p>批量操作时经常出现「几十项里有两三样这次不买」，若只允许正数，
	 * 确认人只能先把成员报的那几行移除 —— 但那会连带删掉别人报的需求，
	 * 且成员端看不到「为什么我的商品没了」。核定 0 把"不采"表达成一次
	 * 可追溯的核定结果，成员在详情里能看到自己报的那项被核定为不采。
	 */
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
				if (approved < 0) {
					throw new ArynBusinessException("核定数量不能为负数");
				}
				item.setApprovedQuantity(approved);
				item.setStatus(SharedCartItem.ITEM_CONFIRMED);
				sharedCartItemMapper.updateById(item);
			}
		}
	}

	private CreateOrderDTO buildCreateOrder(SharedCart cart, List<CreateOrderSkuReqDTO> skuReqList,
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
			// 货到付款透传给 createOrder 校验（共享车固定 deliveryWay=4 内部配送，允许 COD）：
			// 选 COD 时下单即进待发货并直接创建配送任务，不产生待付款单
			createOrderDTO.setPaymentType(confirmDTO.getPaymentType());
		}
		createOrderDTO.setSkuReqList(skuReqList);
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
	 *
	 * <p>核定数量为 0 的行（确认人标记「本次不采」）不生成订单明细。
	 */
	private List<CreateOrderSkuReqDTO> splitByMember(List<SharedCartItem> items) {
		Map<String, String> memberNames = loadMemberDisplayNames(items.get(0).getCartId(), items);
		List<CreateOrderSkuReqDTO> requests = new ArrayList<>(items.size());
		for (SharedCartItem item : items) {
			int quantity = item.getApprovedQuantity() != null ? item.getApprovedQuantity()
					: item.getRequestedQuantity();
			if (quantity <= 0) {
				continue;
			}
			CreateOrderSkuReqDTO skuReq = new CreateOrderSkuReqDTO();
			skuReq.setSkuId(item.getSkuId());
			skuReq.setSpuId(item.getSpuId());
			skuReq.setQuantity(quantity);
			skuReq.setContributorUserId(item.getUserId());
			// 归属姓名（接龙代报）优先于成员姓名链：标签贴的是接龙里那个人
			skuReq.setContributorName(StringUtils.hasText(item.getAttributedName())
					? item.getAttributedName()
					: resolveContributorName(item.getUserId(), memberNames));
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


	// ---------------------------------------------------------------------
	// 补给单 Excel 导入（C2）
	// ---------------------------------------------------------------------

	/**
	 * 解析 + 匹配 + 落报告。
	 *
	 * <p>三步：Excel 解析（订单域）→ 商品域批量匹配 → 报告分类落库。
	 * 分类口径统一走 {@link ReplenishImportClassifier}，与该行确认时的校验同一份规则，
	 * 避免「报告说 OK、确认时又被拦下」。
	 *
	 * <p>解析同步完成，因此**不返回假的解析进度**：前端用不确定态 loading 表达
	 * 「正在解析」，真实进度属于二期（需求确认单 §5）。
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCartImportVO previewImport(String tenantId, String userId, String cartId, String fileName,
			long fileSize, byte[] fileBytes) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireEditable(cart);
		requireNotExpired(cart);
		// 导入是「排计划」的批量形态：确认人/发起人职责，普通成员只能报自己的需求
		requireConfirmer(cart, userId);

		List<ReplenishImportExcel.ParsedRow> parsed = ReplenishImportExcel.parse(
				new ByteArrayInputStream(fileBytes));
		List<ReplenishImportMatchDTO> requests = new ArrayList<>(parsed.size());
		for (int index = 0; index < parsed.size(); index++) {
			ReplenishImportExcel.ParsedRow row = parsed.get(index);
			ReplenishImportMatchDTO request = new ReplenishImportMatchDTO();
			request.setRowNo(index + 1);
			request.setCode(row.getCode());
			request.setName(row.getName());
			request.setSpec(row.getSpec());
			request.setQuantity(ReplenishMatchRules.parseQuantity(row.getQuantityText()));
			requests.add(request);
		}

		Map<Integer, ReplenishImportMatchVO> matchByRowNo = new LinkedHashMap<>();
		try {
			List<ReplenishImportMatchVO> matches = remoteReplenishImportMatchService.matchRows(tenantId, requests);
			for (int index = 0; index < matches.size(); index++) {
				ReplenishImportMatchVO match = matches.get(index);
				if (match == null) {
					continue;
				}
				// 商品域按契约回显 rowNo；万一没回显，按返回顺序对齐，
				// 否则整份报告会静默退化成"全部未匹配"，用户只会以为商品都没建档
				Integer rowNo = match.getRowNo() != null ? match.getRowNo() : index + 1;
				matchByRowNo.put(rowNo, match);
			}
		}
		catch (Exception exception) {
			// 商品域抖动时按「全部未匹配」出报告：用户仍能人工补选，
			// 好过整次上传直接失败（与批量加购的降级口径一致）
			log.warn("补给单导入匹配失败，按未匹配处理 cart={}", cartId, exception);
		}

		SharedCartImport job = new SharedCartImport();
		job.setId(IdWorker.getIdStr());
		job.setCartId(cartId);
		job.setFileName(fileName);
		job.setFileSize(fileSize);
		job.setFileSha256(sha256(fileBytes));
		job.setTotalRows(parsed.size());
		job.setStatus(SharedCartImport.STATUS_PENDING);
		job.setOperatorUserId(userId);
		job.setTenantId(tenantId);
		job.setCreateTime(LocalDateTime.now());
		job.setDelFlag("0");

		int matched = 0;
		int unmatched = 0;
		int specChanged = 0;
		int overStock = 0;
		int invalid = 0;
		int offShelf = 0;
		int notFilled = 0;
		List<SharedCartImportRow> entities = new ArrayList<>(parsed.size());
		for (int index = 0; index < parsed.size(); index++) {
			ReplenishImportExcel.ParsedRow row = parsed.get(index);
			int rowNo = index + 1;
			ReplenishImportMatchVO match = matchByRowNo.get(rowNo);
			Integer quantity = ReplenishMatchRules.parseQuantity(row.getQuantityText());
			// 分类以「数量列原文」为输入：空值代表客户不采购这一行，
			// 与「填了非法数字」是两回事（见 ReplenishImportClassifier）
			ReplenishImportClassifier.Result classified = ReplenishImportClassifier.classify(match, row.getSpec(),
					row.getQuantityText());

			SharedCartImportRow entity = new SharedCartImportRow();
			entity.setId(IdWorker.getIdStr());
			entity.setImportId(job.getId());
			entity.setCartId(cartId);
			entity.setRowNo(rowNo);
			entity.setRawCode(row.getCode());
			entity.setRawName(row.getName());
			entity.setRawSpec(row.getSpec());
			entity.setRawQuantity(quantity);
			// 「单位」列随船供包装资料下线移除，raw_unit 列保留但恒空
			entity.setRawUnit(null);
			entity.setRawRemark(row.getRemark());
			entity.setMatchType(match == null ? "NONE" : match.getMatchType());
			entity.setMatchedSkuId(match == null ? null : match.getSkuId());
			entity.setMatchedSpuId(match == null ? null : match.getSpuId());
			entity.setMatchedName(match == null ? null : match.getName());
			entity.setMatchedSpec(match == null ? null : match.getSpec());
			// 船供包装资料下线后无采购单位来源，matchedUnit 列保留但恒空
			entity.setMatchedUnit(null);
			entity.setMatchedPrice(match == null ? null : match.getSalesPrice());
			entity.setMatchedStock(match == null ? null : match.getStock());
			entity.setQuantity(quantity);
			entity.setSuggestedQuantity(classified.suggestedQuantity());
			entity.setResultType(classified.resultType());
			entity.setResultMessage(classified.message());
			entity.setTenantId(tenantId);
			entity.setCreateTime(LocalDateTime.now());
			entity.setDelFlag("0");
			sharedCartImportRowMapper.insert(entity);
			entities.add(entity);

			switch (classified.resultType()) {
				case SharedCartImportRow.RESULT_OK -> matched++;
				case SharedCartImportRow.RESULT_UNMATCHED -> unmatched++;
				case SharedCartImportRow.RESULT_SPEC_CHANGED -> specChanged++;
				case SharedCartImportRow.RESULT_OVER_STOCK -> overStock++;
				case SharedCartImportRow.RESULT_INVALID_QTY -> invalid++;
				case SharedCartImportRow.RESULT_OFF_SHELF -> offShelf++;
				case SharedCartImportRow.RESULT_NOT_FILLED -> notFilled++;
				default -> invalid++;
			}
		}

		job.setMatchedRows(matched);
		job.setUnmatchedRows(unmatched);
		job.setSpecChangedRows(specChanged);
		job.setOverStockRows(overStock);
		job.setInvalidRows(invalid);
		job.setOffShelfRows(offShelf);
		job.setNotFilledRows(notFilled);
		sharedCartImportMapper.insert(job);
		log.info("补给单导入解析完成 cart={} import={} 行数={} 匹配={} 未匹配={} 未填数量={} 超库存={}", cartId, job.getId(),
				parsed.size(), matched, unmatched, notFilled, overStock);
		return toImportVO(job, entities);
	}

	/**
	 * 接龙粘贴导入：解析「人 × 商品 × 数量」后走与 Excel 导入同一套报告/确认链路。
	 *
	 * <p>接龙里的人大多不是系统用户，因此解析出的人名先按**原文**落行：
	 * 确认并入时才尝试按成员自填姓名精确匹配成真实成员，匹配不上就作为
	 * 归属姓名标签挂在操作者明细上（{@code shared_cart_item.attributed_name}）。
	 * 数量解析失败的行默认按 1 处理——报错会让人卡死在粘贴这一步，
	 * 而默认值有解析疑点提示兜着，报告页一眼能核对。
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCartImportVO previewChainImport(String tenantId, String userId, String cartId, String text) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireEditable(cart);
		requireNotExpired(cart);
		// 与 Excel 导入同口径：导入是「排计划」的批量形态，确认人/发起人职责
		requireConfirmer(cart, userId);

		List<ChainOrderTextParser.ParsedItem> parsed = ChainOrderTextParser.parse(text);
		if (parsed.isEmpty()) {
			throw new ArynBusinessException("没有从文本中解析出报货内容，请检查接龙格式");
		}
		if (parsed.size() > ReplenishImportExcel.MAX_ROWS) {
			throw new ArynBusinessException("接龙内容最多支持 " + ReplenishImportExcel.MAX_ROWS + " 项报货");
		}

		List<ReplenishImportMatchDTO> requests = new ArrayList<>(parsed.size());
		for (int index = 0; index < parsed.size(); index++) {
			ChainOrderTextParser.ParsedItem item = parsed.get(index);
			ReplenishImportMatchDTO request = new ReplenishImportMatchDTO();
			request.setRowNo(index + 1);
			request.setName(item.goodsName());
			request.setQuantity(item.quantity() == null ? 1 : item.quantity());
			requests.add(request);
		}

		Map<Integer, ReplenishImportMatchVO> matchByRowNo = new LinkedHashMap<>();
		try {
			List<ReplenishImportMatchVO> matches = remoteReplenishImportMatchService.matchRows(tenantId, requests);
			for (int index = 0; index < matches.size(); index++) {
				ReplenishImportMatchVO match = matches.get(index);
				if (match == null) {
					continue;
				}
				// 商品域按契约回显 rowNo；万一没回显，按返回顺序对齐
				Integer rowNo = match.getRowNo() != null ? match.getRowNo() : index + 1;
				matchByRowNo.put(rowNo, match);
			}
		}
		catch (Exception exception) {
			// 商品域抖动时按「全部未匹配」出报告：用户仍能人工补选
			log.warn("接龙导入商品匹配失败，按未匹配处理 cart={}", cartId, exception);
		}

		SharedCartImport job = new SharedCartImport();
		job.setId(IdWorker.getIdStr());
		job.setCartId(cartId);
		job.setFileName("微信群接龙粘贴");
		job.setFileSize((long) text.getBytes(StandardCharsets.UTF_8).length);
		job.setFileSha256(sha256(text.getBytes(StandardCharsets.UTF_8)));
		job.setTotalRows(parsed.size());
		job.setStatus(SharedCartImport.STATUS_PENDING);
		job.setOperatorUserId(userId);
		job.setTenantId(tenantId);
		job.setCreateTime(LocalDateTime.now());
		job.setDelFlag("0");

		int matched = 0;
		int unmatched = 0;
		int specChanged = 0;
		int overStock = 0;
		int invalid = 0;
		int offShelf = 0;
		List<SharedCartImportRow> entities = new ArrayList<>(parsed.size());
		for (int index = 0; index < parsed.size(); index++) {
			ChainOrderTextParser.ParsedItem item = parsed.get(index);
			int rowNo = index + 1;
			ReplenishImportMatchVO match = matchByRowNo.get(rowNo);
			int quantity = item.quantity() == null ? 1 : item.quantity();
			// 接龙没有规格列，规格比对恒放行；数量解析失败已在 quantity 兜底为 1
			ReplenishImportClassifier.Result classified = ReplenishImportClassifier.classify(match, null,
					String.valueOf(quantity));
			// 解析疑点（默认数量/「各」/连写合并）随结果说明带出，报告页核对就靠它
			String message = item.warning() == null ? classified.message()
					: classified.message() + "；解析疑点：" + item.warning();

			SharedCartImportRow entity = new SharedCartImportRow();
			entity.setId(IdWorker.getIdStr());
			entity.setImportId(job.getId());
			entity.setCartId(cartId);
			entity.setRowNo(rowNo);
			entity.setSourceType(SharedCartImportRow.SOURCE_CHAIN);
			entity.setPersonName(item.personName());
			entity.setRawName(item.goodsName());
			entity.setRawQuantity(quantity);
			entity.setRawUnit(item.unit());
			entity.setRawRemark(item.rawSegment());
			entity.setMatchType(match == null ? "NONE" : match.getMatchType());
			entity.setMatchedSkuId(match == null ? null : match.getSkuId());
			entity.setMatchedSpuId(match == null ? null : match.getSpuId());
			entity.setMatchedName(match == null ? null : match.getName());
			entity.setMatchedSpec(match == null ? null : match.getSpec());
			entity.setMatchedPrice(match == null ? null : match.getSalesPrice());
			entity.setMatchedStock(match == null ? null : match.getStock());
			entity.setQuantity(quantity);
			entity.setSuggestedQuantity(classified.suggestedQuantity());
			entity.setResultType(classified.resultType());
			entity.setResultMessage(message);
			entity.setTenantId(tenantId);
			entity.setCreateTime(LocalDateTime.now());
			entity.setDelFlag("0");
			sharedCartImportRowMapper.insert(entity);
			entities.add(entity);

			switch (classified.resultType()) {
				case SharedCartImportRow.RESULT_OK -> matched++;
				case SharedCartImportRow.RESULT_UNMATCHED -> unmatched++;
				case SharedCartImportRow.RESULT_SPEC_CHANGED -> specChanged++;
				case SharedCartImportRow.RESULT_OVER_STOCK -> overStock++;
				case SharedCartImportRow.RESULT_INVALID_QTY -> invalid++;
				case SharedCartImportRow.RESULT_OFF_SHELF -> offShelf++;
				default -> invalid++;
			}
		}

		job.setMatchedRows(matched);
		job.setUnmatchedRows(unmatched);
		job.setSpecChangedRows(specChanged);
		job.setOverStockRows(overStock);
		job.setInvalidRows(invalid);
		job.setOffShelfRows(offShelf);
		job.setNotFilledRows(0);
		sharedCartImportMapper.insert(job);
		log.info("接龙导入解析完成 cart={} import={} 行数={} 匹配={} 未匹配={} 人名缺失={}", cartId, job.getId(),
				parsed.size(), matched, unmatched,
				parsed.stream().filter(item -> item.personName() == null).count());
		return toImportVO(job, entities);
	}

	@Override
	public SharedCartImportVO getImport(String tenantId, String userId, String cartId, String importId) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireMembership(cart, userId);
		SharedCartImport job = requireImport(tenantId, cartId, importId);
		return toImportVO(job, listImportRows(tenantId, job.getId()));
	}

	@Override
	public List<SharedCartImportVO> listImports(String tenantId, String userId, String cartId) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireMembership(cart, userId);
		List<SharedCartImport> jobs = sharedCartImportMapper.selectList(Wrappers.lambdaQuery(SharedCartImport.class)
			.eq(SharedCartImport::getTenantId, tenantId)
			.eq(SharedCartImport::getCartId, cartId)
			.orderByDesc(SharedCartImport::getCreateTime));
		List<SharedCartImportVO> result = new ArrayList<>(jobs.size());
		for (SharedCartImport job : jobs) {
			// 列表不带行明细：一次拉回几百行没有意义，点开报告再取 getImport
			result.add(toImportVO(job, null));
		}
		return result;
	}

	/**
	 * 确认并入：按行处置**重新校验**后写入 shared_cart_item。
	 *
	 * <p>幂等靠任务状态：已并入直接返回首次报告，不重复累加数量 ——
	 * 重复点「确认并入」时用户最容易察觉的缺陷就是数量翻倍。
	 *
	 * <p>归属键是「成员用户 + 归属姓名」：Excel 导入行全部归属操作者（与既有
	 * 口径一致）；接龙导入行按人名归到真实成员或操作者+姓名标签。同一归属键的
	 * 多行在此合并；已存在的活动明细累加数量；被逻辑移除（status=3）的明细
	 * **复活**，否则会撞 uk_shared_cart_item 唯一键（该唯一键不含 status，
	 * 物理上只允许一行）。
	 */
	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCartImportVO confirmImport(String tenantId, String userId, String cartId, String importId,
			SharedCartImportConfirmDTO dto) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireEditable(cart);
		requireNotExpired(cart);
		requireConfirmer(cart, userId);
		// 并入写的也是**操作者自己的**明细行（mergeIntoCartItems 按 userId 归属），
		// 所以只读成员不能拿"导入"当绕过 can_edit 的后门。正常配置下确认人
		// can_edit=1（见 create/inviteMember），这里只挡住手工改小的例外。
		requireCanEdit(cart, userId);
		SharedCartImport job = requireImport(tenantId, cartId, importId);
		if (SharedCartImport.STATUS_IMPORTED.equals(job.getStatus())) {
			return toImportVO(job, listImportRows(tenantId, job.getId()));
		}
		if (SharedCartImport.STATUS_CANCELLED.equals(job.getStatus())) {
			throw new ArynBusinessException("该导入已取消，请重新上传");
		}

		Map<Integer, SharedCartImportConfirmDTO.RowAction> actionByRowNo = new LinkedHashMap<>();
		if (dto != null && dto.getRows() != null) {
			for (SharedCartImportConfirmDTO.RowAction action : dto.getRows()) {
				if (action != null && action.getRowNo() != null) {
					actionByRowNo.put(action.getRowNo(), action);
				}
			}
		}

		List<SharedCartImportRow> rows = listImportRows(tenantId, job.getId());
		// 确认时重新取一次商品现状：报告页停留期间商品可能已下架或库存变化
		Map<String, ReplenishImportMatchVO> currentBySkuId = loadCurrentSkus(tenantId, rows, actionByRowNo);

		List<PendingCartMerge> merges = new ArrayList<>();
		// 接龙人名 → 成员：确认时解析一次，整份导入共用同一份词典
		Map<String, String> memberUserIdByName = loadMemberUserIdByName(cartId);
		int skipped = 0;
		for (SharedCartImportRow row : rows) {
			// 未填数量的行是「本次不采购」，不是被跳过的异常行：
			// 不计入 skippedRows，也不写 SKIP 处置痕迹 —— 否则目录模板确认一次，
			// 报告会说「跳过 268 项」，把客户没打算买的东西说成处理失败。
			if (SharedCartImportRow.RESULT_NOT_FILLED.equals(row.getResultType())) {
				continue;
			}
			SharedCartImportConfirmDTO.RowAction action = actionByRowNo.get(row.getRowNo());
			// 匹配成功的行按原型口径已在报告里标为「已入单」，无需用户逐行点确认；
			// 需要处置的行（未匹配/规格变更/超库存/数量异常）未给动作则跳过
			if (action == null && !SharedCartImportRow.RESULT_OK.equals(row.getResultType())) {
				action = null;
			}
			Integer quantity = action == null && SharedCartImportRow.RESULT_OK.equals(row.getResultType())
					? row.getQuantity()
					: resolvedQuantity(row, action);
			String skuId = action == null && SharedCartImportRow.RESULT_OK.equals(row.getResultType())
					? row.getMatchedSkuId()
					: resolvedSkuId(row, action);
			ReplenishImportMatchVO current = skuId == null ? null : currentBySkuId.get(skuId);
			boolean acceptable = current != null && "0".equals(current.getSkuStatus())
					&& "1".equals(current.getSpuStatus())
					&& ReplenishMatchRules.quantityAcceptable(quantity, current.getMoq(), current.getStepQty())
					&& (current.getStock() == null || quantity <= current.getStock());

			if (!acceptable) {
				skipped++;
				row.setResolvedAction(action == null ? SharedCartImportRow.ACTION_SKIP : action.getAction());
				row.setResolvedTime(LocalDateTime.now());
				if (current != null && quantity != null && current.getStock() != null && quantity > current.getStock()) {
					row.setResultMessage("确认时库存已变化（当前 " + current.getStock() + "），本行已跳过");
				}
				sharedCartImportRowMapper.updateById(row);
				continue;
			}

			// 接龙行的归属人名：处置动作可修正（「水手长」→ 真实姓名），修正值落回解析行留痕
			String personName = row.getPersonName();
			if (action != null && StringUtils.hasText(action.getPersonName())) {
				personName = action.getPersonName().strip();
				row.setPersonName(personName);
			}
			row.setResolvedAction(action == null ? SharedCartImportRow.ACTION_ACCEPT_SPEC : action.getAction());
			row.setResolvedSkuId(skuId);
			row.setResolvedQuantity(quantity);
			row.setQuantity(quantity);
			row.setMatchedSkuId(current.getSkuId());
			row.setMatchedSpuId(current.getSpuId());
			row.setMatchedSpec(current.getSpec());
			row.setResolvedTime(LocalDateTime.now());
			sharedCartImportRowMapper.updateById(row);

			// 归属解析：人名精确命中成员自填姓名 → 挂真实成员（他自己能看到并修改这行）；
			// 否则挂操作者 + 归属姓名标签——接龙里的很多人不是系统用户，
			// 配送贴标签、按人分装认的是这个名字，不是用户账号。
			String itemUserId = userId;
			String attributedName = "";
			if (StringUtils.hasText(personName)) {
				String memberUserId = memberUserIdByName.get(personName);
				if (StringUtils.hasText(memberUserId)) {
					itemUserId = memberUserId;
				}
				else {
					attributedName = personName;
				}
			}
			merges.add(new PendingCartMerge(itemUserId, skuId, current.getSpuId(), quantity,
					SharedCartImportRow.SOURCE_EXCEL.equals(row.getSourceType()) && StringUtils.hasText(row.getRawRemark())
							? row.getRawRemark()
							: null,
					attributedName));
		}

		int imported = mergeIntoCartItems(tenantId, cartId, merges);
		job.setStatus(SharedCartImport.STATUS_IMPORTED);
		job.setImportedRows(imported);
		job.setSkippedRows(skipped);
		job.setConfirmedTime(LocalDateTime.now());
		sharedCartImportMapper.updateById(job);
		log.info("补给单导入并入完成 cart={} import={} 并入={} 跳过={}", cartId, importId, imported, skipped);
		return toImportVO(job, listImportRows(tenantId, job.getId()));
	}

	/** 待并入明细：确认阶段逐行产出，同键（归属人+SKU）在写入前合并数量 */
	private record PendingCartMerge(String userId, String skuId, String spuId, Integer quantity, String remark,
			String attributedName) {
	}

	/** 明细归并键：归属人 + SKU + 归属姓名（空姓名归一为空串，与列默认值一致） */
	private static String cartItemKey(String userId, String skuId, String attributedName) {
		return userId + "|" + skuId + "|" + (StringUtils.hasText(attributedName) ? attributedName : "");
	}

	/**
	 * 成员自填姓名 → 用户ID。同名成员多于一个时该名字放弃自动归属
	 * （归属错人比归属成姓名标签更难察觉，交给报告页人工指定）。
	 */
	private Map<String, String> loadMemberUserIdByName(String cartId) {
		List<SharedCartMember> members = sharedCartMemberMapper.selectList(Wrappers.lambdaQuery(SharedCartMember.class)
			.eq(SharedCartMember::getCartId, cartId));
		Map<String, List<String>> userIdsByName = new LinkedHashMap<>();
		for (SharedCartMember member : members) {
			if (!StringUtils.hasText(member.getDisplayName()) || !StringUtils.hasText(member.getUserId())) {
				continue;
			}
			userIdsByName.computeIfAbsent(member.getDisplayName().strip(), key -> new ArrayList<>())
				.add(member.getUserId());
		}
		Map<String, String> result = new HashMap<>(userIdsByName.size());
		userIdsByName.forEach((name, userIds) -> {
			if (userIds.size() == 1) {
				result.put(name, userIds.get(0));
			}
		});
		return result;
	}

	/**
	 * 把待并入明细写进补给单，返回实际写入的明细项数。
	 *
	 * <p>归属键是「成员用户 + 归属姓名」：Excel 导入与本人加购的行归属姓名为空，
	 * 行为与既有口径完全一致；接龙代报的行挂在操作者名下、按归属姓名互相区分，
	 * 同一车同一种商品两个人各占一行（uk_shared_cart_item 已扩列）。
	 *
	 * <p>同一键的多行先合并数量；已存在的活动明细累加数量；被逻辑移除
	 * （status=3）的明细**复活**，否则会撞 uk_shared_cart_item 唯一键
	 * （该唯一键不含 status，物理上只允许一行）。
	 */
	private int mergeIntoCartItems(String tenantId, String cartId, List<PendingCartMerge> merges) {
		if (merges.isEmpty()) {
			return 0;
		}
		// 同键合并数量（Excel 同 SKU 多行、接龙同人同品多行都走到这里）
		Map<String, PendingCartMerge> mergedBykey = new LinkedHashMap<>();
		for (PendingCartMerge merge : merges) {
			mergedBykey.merge(cartItemKey(merge.userId(), merge.skuId(), merge.attributedName()), merge,
					(first, second) -> new PendingCartMerge(first.userId(), first.skuId(), first.spuId(),
							first.quantity() + second.quantity(),
							StringUtils.hasText(second.remark()) ? second.remark() : first.remark(),
							first.attributedName()));
		}

		// 已有的活动/已移除明细一次取出按归属键归并，避免逐条查询
		List<SharedCartItem> existing = sharedCartItemMapper.selectList(Wrappers.lambdaQuery(SharedCartItem.class)
			.eq(SharedCartItem::getTenantId, tenantId)
			.eq(SharedCartItem::getCartId, cartId)
			.in(SharedCartItem::getSkuId, mergedBykey.values().stream().map(PendingCartMerge::skuId).toList()));
		Map<String, SharedCartItem> existingByKey = new LinkedHashMap<>();
		for (SharedCartItem item : existing) {
			existingByKey.putIfAbsent(cartItemKey(item.getUserId(), item.getSkuId(), item.getAttributedName()), item);
		}

		int imported = 0;
		for (PendingCartMerge merge : mergedBykey.values()) {
			int quantity = merge.quantity();
			SharedCartItem item = existingByKey.get(cartItemKey(merge.userId(), merge.skuId(), merge.attributedName()));
			if (item == null) {
				item = new SharedCartItem();
				item.setId(IdWorker.getIdStr());
				item.setCartId(cartId);
				item.setUserId(merge.userId());
				item.setAttributedName(StringUtils.hasText(merge.attributedName()) ? merge.attributedName() : "");
				item.setSpuId(merge.spuId());
				item.setSkuId(merge.skuId());
				item.setRequestedQuantity(quantity);
				item.setStatus(SharedCartItem.ITEM_PENDING);
				item.setMemberRemark(merge.remark());
				item.setTenantId(tenantId);
				item.setCreateTime(LocalDateTime.now());
				item.setDelFlag("0");
				sharedCartItemMapper.insert(item);
			}
			else if (SharedCartItem.ITEM_REMOVED.equals(item.getStatus())) {
				// 复活已移除明细：唯一键不含 status，插入新行会直接撞键
				item.setUserId(merge.userId());
				item.setAttributedName(StringUtils.hasText(merge.attributedName()) ? merge.attributedName() : "");
				item.setSpuId(merge.spuId());
				item.setStatus(SharedCartItem.ITEM_PENDING);
				item.setRequestedQuantity(quantity);
				if (StringUtils.hasText(merge.remark())) {
					item.setMemberRemark(merge.remark());
				}
				item.setUpdateTime(LocalDateTime.now());
				sharedCartItemMapper.updateById(item);
			}
			else {
				// 与已有明细合并数量：导入数量累加进成员申请量
				int currentRequested = item.getRequestedQuantity() == null ? 0 : item.getRequestedQuantity();
				item.setRequestedQuantity(currentRequested + quantity);
				item.setUpdateTime(LocalDateTime.now());
				sharedCartItemMapper.updateById(item);
			}
			imported++;
		}
		return imported;
	}

	/** 行最终数量：调整数量动作取客户端值，其余沿用服务端解析值 */
	private Integer resolvedQuantity(SharedCartImportRow row, SharedCartImportConfirmDTO.RowAction action) {
		if (action == null) {
			return null;
		}
		if (SharedCartImportRow.ACTION_ADJUST_QTY.equals(action.getAction())) {
			return action.getQuantity();
		}
		return row.getQuantity();
	}

	/** 行最终 SKU：人工补选取客户端 SKU，其余动作沿用匹配结果；未处置动作返回 null */
	private String resolvedSkuId(SharedCartImportRow row, SharedCartImportConfirmDTO.RowAction action) {
		if (action == null || !StringUtils.hasText(action.getAction())
				|| SharedCartImportRow.ACTION_SKIP.equals(action.getAction())) {
			return null;
		}
		if (SharedCartImportRow.ACTION_REPLACE_SKU.equals(action.getAction())) {
			return action.getSkuId();
		}
		return row.getMatchedSkuId();
	}

	/**
	 * 一次性取出本次确认涉及的 SKU 现状（不过滤上下架），
	 * 供确认阶段重新校验使用。
	 */
	private Map<String, ReplenishImportMatchVO> loadCurrentSkus(String tenantId, List<SharedCartImportRow> rows,
			Map<Integer, SharedCartImportConfirmDTO.RowAction> actionByRowNo) {
		Set<String> skuIds = new LinkedHashSet<>();
		for (SharedCartImportRow row : rows) {
			SharedCartImportConfirmDTO.RowAction action = actionByRowNo.get(row.getRowNo());
			// 匹配成功的行没有显式动作，按「沿用匹配结果」取 SKU，
			// 否则这些行在回查时被漏掉、确认阶段全部按跳过处理
			String skuId = action == null && SharedCartImportRow.RESULT_OK.equals(row.getResultType())
					? row.getMatchedSkuId()
					: resolvedSkuId(row, action);
			if (StringUtils.hasText(skuId)) {
				skuIds.add(skuId);
			}
		}
		Map<String, ReplenishImportMatchVO> map = new LinkedHashMap<>();
		if (skuIds.isEmpty()) {
			return map;
		}
		try {
			for (ReplenishImportMatchVO match : remoteReplenishImportMatchService.matchSkuIds(tenantId,
					new ArrayList<>(skuIds))) {
				if (match != null && StringUtils.hasText(match.getSkuId())) {
					map.put(match.getSkuId(), match);
				}
			}
		}
		catch (Exception exception) {
			// 查不到就全部按跳过处理：宁可少并入，也不能把不确定的行写进清单
			log.warn("补给单确认时回查商品失败，相关行将跳过 tenantId={}", tenantId, exception);
		}
		return map;
	}

	private SharedCartImport requireImport(String tenantId, String cartId, String importId) {
		if (!StringUtils.hasText(importId)) {
			throw new ArynBusinessException("导入任务ID不能为空");
		}
		SharedCartImport job = sharedCartImportMapper.selectOne(Wrappers.lambdaQuery(SharedCartImport.class)
			.eq(SharedCartImport::getTenantId, tenantId)
			.eq(SharedCartImport::getCartId, cartId)
			.eq(SharedCartImport::getId, importId));
		if (job == null) {
			throw new ArynBusinessException("导入任务不存在");
		}
		return job;
	}

	private List<SharedCartImportRow> listImportRows(String tenantId, String importId) {
		return sharedCartImportRowMapper.selectList(Wrappers.lambdaQuery(SharedCartImportRow.class)
			.eq(SharedCartImportRow::getTenantId, tenantId)
			.eq(SharedCartImportRow::getImportId, importId)
			.orderByAsc(SharedCartImportRow::getRowNo));
	}

	private SharedCartImportVO toImportVO(SharedCartImport job, List<SharedCartImportRow> rows) {
		SharedCartImportVO vo = new SharedCartImportVO();
		vo.setImportId(job.getId());
		vo.setCartId(job.getCartId());
		vo.setFileName(job.getFileName());
		vo.setStatus(job.getStatus());
		vo.setTotalRows(job.getTotalRows());
		vo.setMatchedRows(job.getMatchedRows());
		vo.setUnmatchedRows(job.getUnmatchedRows());
		vo.setSpecChangedRows(job.getSpecChangedRows());
		vo.setOverStockRows(job.getOverStockRows());
		vo.setInvalidRows(job.getInvalidRows());
		vo.setOffShelfRows(job.getOffShelfRows());
		vo.setNotFilledRows(job.getNotFilledRows());
		vo.setImportedRows(job.getImportedRows());
		vo.setSkippedRows(job.getSkippedRows());
		vo.setCreateTime(job.getCreateTime());
		vo.setConfirmedTime(job.getConfirmedTime());
		if (rows == null) {
			return vo;
		}
		List<SharedCartImportRowVO> rowVOs = new ArrayList<>(rows.size());
		for (SharedCartImportRow row : rows) {
			SharedCartImportRowVO rowVO = new SharedCartImportRowVO();
			rowVO.setRowNo(row.getRowNo());
			rowVO.setPersonName(row.getPersonName());
			rowVO.setRawCode(row.getRawCode());
			rowVO.setRawName(row.getRawName());
			rowVO.setRawSpec(row.getRawSpec());
			rowVO.setRawQuantity(row.getRawQuantity());
			rowVO.setRawUnit(row.getRawUnit());
			rowVO.setRawRemark(row.getRawRemark());
			rowVO.setMatchType(row.getMatchType());
			rowVO.setMatchedSkuId(row.getMatchedSkuId());
			rowVO.setMatchedName(row.getMatchedName());
			rowVO.setMatchedSpec(row.getMatchedSpec());
			rowVO.setMatchedUnit(row.getMatchedUnit());
			rowVO.setMatchedPrice(row.getMatchedPrice());
			rowVO.setMatchedStock(row.getMatchedStock());
			rowVO.setQuantity(row.getQuantity());
			rowVO.setResultType(row.getResultType());
			rowVO.setResultMessage(row.getResultMessage());
			rowVO.setResolvedAction(row.getResolvedAction());
			rowVO.setResolvedSkuId(row.getResolvedSkuId());
			rowVO.setResolvedQuantity(row.getResolvedQuantity());
			rowVO.setSuggestedQuantity(row.getSuggestedQuantity());
			rowVOs.add(rowVO);
		}
		vo.setRows(rowVOs);
		return vo;
	}

	/** 文件内容摘要：仅用于「是不是同一个文件」的提示，不做去重拦截 */
	private static String sha256(byte[] bytes) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
			StringBuilder builder = new StringBuilder(digest.length * 2);
			for (byte value : digest) {
				builder.append(Character.forDigit((value >> 4) & 0xF, 16));
				builder.append(Character.forDigit(value & 0xF, 16));
			}
			return builder.toString();
		}
		catch (NoSuchAlgorithmException exception) {
			// JDK 必带 SHA-256；真出现也只是失去"同一文件"提示能力
			return null;
		}
	}

}
