package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.dto.CreateOrderDTO;
import com.aryn.cloud.order.api.dto.CreateOrderSkuReqDTO;
import com.aryn.cloud.order.api.dto.SharedCartItemDTO;
import com.aryn.cloud.order.api.dto.SharedCartConfirmDTO;
import com.aryn.cloud.order.api.dto.SharedCartCreateDTO;
import com.aryn.cloud.order.api.entity.SharedCart;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.ISharedCartService;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.product.api.entity.ShipSkuProfile;
import com.aryn.cloud.product.api.remote.RemoteShipProductProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

	private final SharedCartMapper sharedCartMapper;

	private final SharedCartMemberMapper sharedCartMemberMapper;

	private final SharedCartItemMapper sharedCartItemMapper;

	private final IOrderInfoService orderInfoService;

	@DubboReference
	private RemoteShipProductProfileService remoteShipProductProfileService;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCart create(String tenantId, String userId, SharedCartCreateDTO dto) {
		SharedCart cart = new SharedCart();
		cart.setId(IdWorker.getIdStr());
		cart.setCartNo("SC" + IdWorker.getIdStr());
		cart.setVesselId(dto.getVesselId());
		cart.setVesselCallId(dto.getVesselCallId());
		cart.setOwnerUserId(userId);
		cart.setConfirmerUserId(userId);
		cart.setStatus(SharedCart.STATUS_COLLECTING);
		cart.setExpiresAt(dto.getExpiresAt());
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
	public List<SharedCartMember> listMembers(String tenantId, String cartId) {
		return sharedCartMemberMapper.selectList(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, tenantId)
				.eq(SharedCartMember::getCartId, cartId)
				.orderByAsc(SharedCartMember::getJoinedTime));
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
		createOrderDTO.setSkuReqList(aggregateBySku(items));
		return createOrderDTO;
	}

	/**
	 * 按 SKU 聚合成员明细：数量求和，贡献者与备注合并快照（整船合并订单的核心规则）。
	 */
	private List<CreateOrderSkuReqDTO> aggregateBySku(List<SharedCartItem> items) {
		Map<String, CreateOrderSkuReqDTO> merged = new LinkedHashMap<>();
		for (SharedCartItem item : items) {
			int quantity = item.getApprovedQuantity() != null ? item.getApprovedQuantity()
					: item.getRequestedQuantity();
			CreateOrderSkuReqDTO skuReq = merged.get(item.getSkuId());
			if (skuReq == null) {
				skuReq = new CreateOrderSkuReqDTO();
				skuReq.setSkuId(item.getSkuId());
				skuReq.setSpuId(item.getSpuId());
				skuReq.setQuantity(quantity);
				skuReq.setContributorUserId(item.getUserId());
				skuReq.setMemberRemark(item.getMemberRemark());
				merged.put(item.getSkuId(), skuReq);
			}
			else {
				skuReq.setQuantity(skuReq.getQuantity() + quantity);
				skuReq.setContributorUserId(skuReq.getContributorUserId() + "," + item.getUserId());
				if (StringUtils.hasText(item.getMemberRemark())) {
					String remark = StringUtils.hasText(skuReq.getMemberRemark())
							? skuReq.getMemberRemark() + "；" + item.getMemberRemark()
							: item.getMemberRemark();
					skuReq.setMemberRemark(remark.length() > 255 ? remark.substring(0, 255) : remark);
				}
			}
		}
		return List.copyOf(merged.values());
	}

}
