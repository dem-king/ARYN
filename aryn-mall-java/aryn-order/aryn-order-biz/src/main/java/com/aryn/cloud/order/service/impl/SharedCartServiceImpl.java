package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartVO;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.ISharedCartService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
