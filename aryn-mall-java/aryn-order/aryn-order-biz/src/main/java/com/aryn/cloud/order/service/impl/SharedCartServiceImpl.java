package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
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
import com.aryn.cloud.order.api.entity.BuyerOrg;
import com.aryn.cloud.order.api.entity.SharedCartImport;
import com.aryn.cloud.order.api.entity.SharedCartImportRow;
import com.aryn.cloud.order.api.entity.SharedCartItem;
import com.aryn.cloud.order.api.entity.SharedCartMember;
import com.aryn.cloud.order.api.vo.SharedCartImportRowVO;
import com.aryn.cloud.order.api.vo.SharedCartImportVO;
import com.aryn.cloud.order.api.vo.SharedCartItemVO;
import com.aryn.cloud.order.api.vo.SharedCartJoinVO;
import com.aryn.cloud.order.api.vo.SharedCartMemberCandidateVO;
import com.aryn.cloud.order.api.vo.SharedCartReuseVO;
import com.aryn.cloud.order.api.vo.SharedCartSummaryVO;
import com.aryn.cloud.order.api.vo.SharedCartVO;
import com.aryn.cloud.order.mapper.SharedCartImportMapper;
import com.aryn.cloud.order.mapper.SharedCartImportRowMapper;
import com.aryn.cloud.order.mapper.SharedCartItemMapper;
import com.aryn.cloud.order.mapper.SharedCartMapper;
import com.aryn.cloud.order.mapper.SharedCartMemberMapper;
import com.aryn.cloud.order.service.IOrderInfoService;
import com.aryn.cloud.order.service.IBuyerOrgService;
import com.aryn.cloud.order.service.ISharedCartService;
import com.aryn.cloud.order.service.IDeliveryAreaService;
import com.aryn.cloud.order.service.SharedCartInviteNotifyService;
import com.aryn.cloud.pay.api.utils.TransactionalMqUtils;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.product.api.entity.GoodsSku;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.remote.RemoteGoodsSkuService;
import com.aryn.cloud.product.api.remote.RemoteGoodsSpuService;
import com.aryn.cloud.product.api.remote.RemoteReplenishImportMatchService;
import com.aryn.cloud.product.api.support.ReplenishMatchRules;
import com.aryn.cloud.product.api.vo.ReplenishImportMatchVO;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.aryn.cloud.vessel.api.remote.RemoteVesselService;
import com.aryn.cloud.vessel.api.vo.VesselMemberVO;
import com.aryn.cloud.user.api.entity.UserAddress;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.remote.RemoteUserAddressService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

	/**
	 * 船舶域管理角色口径：发起人(1)/采购确认人(3)/业务员(4)，与 vessel 服务
	 * requireMemberAdmin 的口径一致。这些角色并入同船收集中车时天然获得
	 * 提交权（业务员代下单场景：船员建车收集、业务员后到并提交）。
	 */
	private static final Set<String> VESSEL_MANAGER_ROLES = Set.of(
			VesselMember.ROLE_OWNER, VesselMember.ROLE_CONFIRMER, VesselMember.ROLE_SALES);

	private final SharedCartMapper sharedCartMapper;

	private final SharedCartMemberMapper sharedCartMemberMapper;

	private final SharedCartItemMapper sharedCartItemMapper;

	private final SharedCartImportMapper sharedCartImportMapper;

	private final SharedCartImportRowMapper sharedCartImportRowMapper;

	private final IOrderInfoService orderInfoService;

	/** 配送范围（岸上车地址在创建与提交两处校验，范围口径与普通订单 way=3 一致） */
	private final IDeliveryAreaService deliveryAreaService;

	/** 采购主体：岸上车创建必须绑定主体，创建人须为该主体有效成员 */
	private final IBuyerOrgService buyerOrgService;

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

	/** 用户域：岸上车创建/提交时按归属取收货地址（getById 两参带 userId 归属校验） */
	@DubboReference
	private RemoteUserAddressService remoteUserAddressService;

	/** 邀请成员的站内通知（发送时机由本服务用 TransactionalMqUtils 包在提交后） */
	private final SharedCartInviteNotifyService sharedCartInviteNotifyService;

    private final com.aryn.cloud.order.support.procurement.ProcurementContributionLedger procurementLedger;

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCart create(String tenantId, String userId, SharedCartCreateDTO dto) {
		boolean shoreCart = SharedCart.CART_TYPE_SHORE.equals(dto.normalizedCartType());
		String vesselRole = null;
		UserAddress address = null;
		BuyerOrg buyerOrg = null;
		if (shoreCart) {
			// 岸上企业车：收货人地址是配送终点，必须取发起人地址簿里的地址
			// （getById 两参带 userId 归属校验，不能借别人的地址建车），且须在公司配送范围内。
			address = requireShoreAddress(tenantId, userId, dto.getUserAddressId());
			// 岸上企业车必须先有采购主体：主体不存在/停用/驳回、或发起人不是有效成员都会被拦。
			// 待审核主体放行（可先下企业单），是否享首单由 P1 引擎按 audit_status 判。
			buyerOrg = buyerOrgService.requireUsableForCart(tenantId, userId, dto.getBuyerOrgId());
		}
		else {
			// 发起人必须是该船在船成员：前端只对已选船+靠港的用户开放创建入口，但入参不可信。
			// 确认下单环节的配送校验兜底太晚——到那时明细已收集（窗口最长 24 小时），
			// 且同船唯一收集中车的规则会让真成员被这张永远提交不了的车挡住。
			// 校验必须放在同船去重之前：否则非成员先命中已有车，拿到一张自己无权查看的详情。
			if (!StringUtils.hasText(dto.getVesselId()) || !StringUtils.hasText(dto.getVesselCallId())) {
				throw new ArynBusinessException("请选择船舶和靠港计划后再发起共享购物车");
			}
			vesselRole = remoteVesselService.getVesselMemberRole(tenantId, dto.getVesselId(), userId);
			if (!StringUtils.hasText(vesselRole)) {
				throw new ArynBusinessException("您不是该船舶成员，无法发起共享购物车");
			}
		}

		// 同车去重：船供车按船舶（同船同时只允许一个「收集中」，否则同船同一靠港会各下各的单）；
		// 岸上车按「发起人 + 采购主体」（同一发起人可在不同工厂各开一张车，
		// 同一工厂仍只允许一张收集中车，数据库兜底见 uk_shared_cart_shore_scope）。命中时返回已有购物车并自动并入，
		// 由前端打开该车（并发场景由唯一索引兜底）。
		SharedCart existing = shoreCart
				? findActiveShoreCart(tenantId, userId, buyerOrg.getId())
				: findActiveShipCart(tenantId, dto.getVesselId());
		if (existing != null) {
			log.info("{}共享购物车[{}]已有进行中记录，创建请求被引导至已有购物车",
					shoreCart ? "岸上" : "船舶[" + dto.getVesselId() + "]", existing.getId());
			existing.setAdoptedExisting(Boolean.TRUE);
			adoptExistingCart(existing, userId, vesselRole);
			return existing;
		}

		SharedCart cart = new SharedCart();
		cart.setId(IdWorker.getIdStr());
		cart.setCartNo("SC" + IdWorker.getIdStr());
		cart.setCartType(shoreCart ? SharedCart.CART_TYPE_SHORE : SharedCart.CART_TYPE_SHIP);
		cart.setVesselId(shoreCart ? null : dto.getVesselId());
		cart.setVesselCallId(shoreCart ? null : dto.getVesselCallId());
		cart.setUserAddressId(shoreCart ? address.getId() : null);
		cart.setAddressSnapshot(shoreCart ? buildAddressSnapshot(address) : null);
		cart.setBuyerOrgId(shoreCart ? buyerOrg.getId() : null);
		cart.setBuyerOrgName(shoreCart ? buyerOrg.getOrgName() : null);
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

	/** 岸上车地址校验：必填、属于发起人、在公司配送（岸上）范围内；通过后返回地址实体。 */
	private UserAddress requireShoreAddress(String tenantId, String userId, String userAddressId) {
		if (!StringUtils.hasText(userAddressId)) {
			throw new ArynBusinessException("请选择收货地址后再发起共享购物车");
		}
		UserAddress address = remoteUserAddressService.getById(userAddressId, userId);
		if (address == null) {
			throw new ArynBusinessException("收货地址不存在或不属于你，请重新选择");
		}
		if (!deliveryAreaService.isAddressInDeliveryArea(address.getProvinceCode(), address.getCityCode(),
				address.getAreaCode())) {
			throw new ArynBusinessException("当前收货地址不在公司配送（岸上）范围内");
		}
		return address;
	}

	/** 地址快照拼接：省市区 + 详址 + 收货人电话，列表/详情展示不再回查地址簿。 */
	private String buildAddressSnapshot(UserAddress address) {
		StringBuilder sb = new StringBuilder();
		for (String part : new String[] { address.getProvinceName(), address.getCityName(), address.getAreaName(),
				address.getDetailAddress() }) {
			if (StringUtils.hasText(part)) {
				sb.append(part);
			}
		}
		if (StringUtils.hasText(address.getRecipientName())) {
			sb.append(" ").append(address.getRecipientName());
		}
		if (StringUtils.hasText(address.getTelephone())) {
			sb.append(" ").append(address.getTelephone());
		}
		return sb.toString();
	}

	/** 同船收集中车：同船唯一，命中即复用。 */
	private SharedCart findActiveShipCart(String tenantId, String vesselId) {
		return sharedCartMapper.selectOne(Wrappers.lambdaQuery(SharedCart.class)
				.eq(SharedCart::getTenantId, tenantId)
				.eq(SharedCart::getVesselId, vesselId)
				.eq(SharedCart::getStatus, SharedCart.STATUS_COLLECTING)
				.last("LIMIT 1"));
	}

	/** 发起人在指定主体下的岸上收集中车：同一工厂只收敛为一张，不同主体互不影响。 */
	private SharedCart findActiveShoreCart(String tenantId, String ownerUserId, String buyerOrgId) {
		return sharedCartMapper.selectOne(Wrappers.lambdaQuery(SharedCart.class)
				.eq(SharedCart::getTenantId, tenantId)
				.eq(SharedCart::getCartType, SharedCart.CART_TYPE_SHORE)
				.eq(SharedCart::getOwnerUserId, ownerUserId)
				.eq(SharedCart::getBuyerOrgId, buyerOrgId)
				.eq(SharedCart::getStatus, SharedCart.STATUS_COLLECTING)
				.last("LIMIT 1"));
	}

	/**
	 * 命中同船收集中车时把发起请求的船成员并入：无成员行则新增（可编辑）。
	 *
	 * <p>此前 adopt 只返回车 ID，非成员拿到的是一张自己无权查看的车——前端提示
	 * 「已为你打开」随即详情接口权限报错，想参与还得等车主邀请。船成员身份在
	 * create 入口已校验，并入的可信度与凭邀请码加入同级。
	 *
	 * <p>已在车里的成员不动车主给定的编辑权（{@code can_edit=0} 是车主显式收回，
	 * 不能借「再次发起」绕过，与 {@link #requireReuseEditable} 同一道门槛）；
	 * 仅给船舶管理角色（发起人/采购确认人/业务员）补确认权——业务员代下单的
	 * 主场景就是船员先建车收集、业务员后到提交，不给确认权则代下单断在最后一步。
	 */
	private void adoptExistingCart(SharedCart cart, String userId, String vesselRole) {
		// 岸上企业车没有船舶域角色（vesselRole=null）：按普通成员并入，不补确认权；
		// Set.of 的 contains 也不接受 null，先判空再查
		boolean vesselManager = vesselRole != null && VESSEL_MANAGER_ROLES.contains(vesselRole);
		SharedCartMember member = sharedCartMemberMapper.selectOne(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, cart.getTenantId())
				.eq(SharedCartMember::getCartId, cart.getId())
				.eq(SharedCartMember::getUserId, userId)
				.last("LIMIT 1"));
		if (member == null) {
			SharedCartMember joined = new SharedCartMember();
			joined.setCartId(cart.getId());
			joined.setUserId(userId);
			joined.setMemberRole(vesselManager ? SharedCartMember.ROLE_CONFIRMATOR : SharedCartMember.ROLE_MEMBER);
			joined.setCanEdit("1");
			joined.setCanConfirm(vesselManager ? "1" : "0");
			joined.setJoinedTime(LocalDateTime.now());
			joined.setTenantId(cart.getTenantId());
			joined.setCreateTime(LocalDateTime.now());
			joined.setDelFlag("0");
			sharedCartMemberMapper.insert(joined);
			log.info("船成员并入同船收集中车 cart={} user={} 可确认={}", cart.getId(), userId, vesselManager);
			return;
		}
		if (vesselManager && !"1".equals(member.getCanConfirm())) {
			member.setCanConfirm("1");
			sharedCartMemberMapper.updateById(member);
			log.info("船舶管理角色并入同船收集中车并补齐确认权 cart={} user={}", cart.getId(), userId);
		}
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
		// 用户存在性校验：选人邀请来自候选接口，但入参仍不可信（历史上是手填用户 ID）。
		// 不校验会静默落一条无效成员行——对方永远无感知，发起人还以为邀请成功。
		UserInfoVO invitedUser = requireInvitableUser(tenantId, memberUserId);
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
		log.info("发起人[{}]邀请用户[{}]加入共享购物车[{}]，角色={}", operatorUserId, memberUserId, cartId,
				member.getMemberRole());
		// 通知放提交后：回滚的邀请不发送；无事务上下文时立即执行（单测可直接验证）。
		// 船名查询失败不影响邀请——通知里省略船名即可（resolveVesselNameForJoin 内部已兜底）。
		boolean shoreCart = isShoreCart(cart);
		String vesselName = shoreCart ? null : resolveVesselNameForJoin(tenantId, cart);
		TransactionalMqUtils.sendAfterCommit(() -> sharedCartInviteNotifyService.notifyInvited(tenantId, cartId,
				shoreCart, vesselName, memberUserId, invitedUser.getNickname()));
		return member;
	}

	/**
	 * 邀请对象必须是真实存在且属于本租户的用户；用户域不可用时拒绝——
	 * 宁可让发起人稍后重试，也不落一条对方永远无感知的无效成员行。
	 */
	private UserInfoVO requireInvitableUser(String tenantId, String memberUserId) {
		UserInfoVO user;
		try {
			user = remoteMallUserService.getUserById(memberUserId);
		}
		catch (Exception e) {
			log.warn("邀请成员时查询用户失败：userId={}", memberUserId, e);
			throw new ArynBusinessException("邀请失败，请稍后重试");
		}
		if (user == null || !StringUtils.hasText(user.getId())) {
			throw new ArynBusinessException("该用户不存在，请从候选列表中选择后再邀请");
		}
		// 租户一致性：跨租户用户即使真实存在，也进不了本租户的采购链路；
		// 历史数据的 tenant_id 允许为空，为空时跳过该检查（不误伤）
		if (StringUtils.hasText(user.getTenantId()) && !user.getTenantId().equals(tenantId)) {
			throw new ArynBusinessException("对方不属于当前企业，无法邀请");
		}
		return user;
	}

	/** 候选人单次返回上限（弹层滚动列表的合理体量，与船舶域 searchCandidates 对齐） */
	private static final int CANDIDATE_LIMIT = 20;

	@Override
	public List<SharedCartMemberCandidateVO> listMemberCandidates(String tenantId, String userId, String cartId,
			String keyword) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireOwner(cart, userId);
		Set<String> existing = listMembers(tenantId, cartId).stream()
			.map(SharedCartMember::getUserId)
			.filter(StringUtils::hasText)
			.collect(Collectors.toSet());
		if (isShoreCart(cart)) {
			return searchShoreCandidates(tenantId, cartId, keyword, existing);
		}
		return listShipCandidates(tenantId, cart, keyword, existing);
	}

	/**
	 * 岸上企业车候选：同租户用户搜索。
	 *
	 * <p>岸上车没有「同船成员」这个天然候选集，关键词必填、不设默认列表——
	 * 空关键词返回空列表，由前端引导发起人输入姓名或手机号。
	 */
	private List<SharedCartMemberCandidateVO> searchShoreCandidates(String tenantId, String cartId, String keyword,
			Set<String> existing) {
		if (!StringUtils.hasText(keyword)) {
			return List.of();
		}
		List<UserInfoVO> users;
		try {
			users = remoteMallUserService.searchTenantUsers(tenantId, keyword.trim(), CANDIDATE_LIMIT);
		}
		catch (Exception e) {
			log.warn("搜索岸上购物车候选成员失败，返回空列表：cartId={} tenantId={}", cartId, tenantId, e);
			return List.of();
		}
		return users.stream()
			.filter(u -> u != null && StringUtils.hasText(u.getId()))
			.map(u -> toCandidateVO(u.getId(), u.getNickname(), u.getAvatarUrl(), maskPhone(u.getPhone()), existing))
			.toList();
	}

	/**
	 * 船供车候选：本船在船成员。
	 *
	 * <p>船员是一份有限且互相认识的名单，无关键词时默认返回全量（打开弹层即见，
	 * 不必先搜索）；姓名/手机号过滤在船舶域完成——原始手机号不出域，出参已脱敏。
	 */
	private List<SharedCartMemberCandidateVO> listShipCandidates(String tenantId, SharedCart cart, String keyword,
			Set<String> existing) {
		if (!StringUtils.hasText(cart.getVesselId())) {
			return List.of();
		}
		List<VesselMemberVO> members;
		try {
			members = remoteVesselService.listOnboardMembers(tenantId, cart.getVesselId(),
					StringUtils.hasText(keyword) ? keyword.trim() : null);
		}
		catch (Exception e) {
			log.warn("查询船供购物车候选成员失败，返回空列表：cartId={} vesselId={}", cart.getId(), cart.getVesselId(), e);
			return List.of();
		}
		return members.stream()
			.filter(vo -> vo != null && StringUtils.hasText(vo.getUserId()))
			.map(vo -> toCandidateVO(vo.getUserId(), vo.getNickname(), null, vo.getPhone(), existing))
			.toList();
	}

	private SharedCartMemberCandidateVO toCandidateVO(String userId, String nickname, String avatarUrl, String phone,
			Set<String> existing) {
		SharedCartMemberCandidateVO vo = new SharedCartMemberCandidateVO();
		vo.setUserId(userId);
		vo.setNickname(nickname);
		vo.setAvatarUrl(avatarUrl);
		vo.setPhone(phone);
		vo.setAlreadyMember(existing.contains(userId));
		return vo;
	}

	/** 手机号脱敏：保留前三后四（与船舶域 VesselBindServiceImpl 同口径）。 */
	private String maskPhone(String phone) {
		if (!StringUtils.hasText(phone) || phone.length() < 7) {
			return phone;
		}
		return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public SharedCartItem addItem(String tenantId, String userId, String cartId, SharedCartItemDTO itemDTO) {
		SharedCart cart = requireCart(tenantId, cartId);
		requireEditable(cart);
		requireNotExpired(cart);
		// 成员身份与明细写入权一次判完（非成员报「无权访问」，只读成员报缺编辑权）
		requireCanEdit(cart, userId);
		// 凡以自己名义落明细必须先有姓名（配送按人贴标签）；C 端详情页有强制弹层，这里是真闸门
		requireMemberDisplayName(cart, userId);
		// 手动加购归属就是本人（归属姓名空串），选货页重复点「加入共享车」预期是累加数量
		return upsertOwnCartItem(tenantId, cartId, userId, "", itemDTO.getSpuId(), itemDTO.getSkuId(),
				itemDTO.getRequestedQuantity(), itemDTO.getMemberRemark(), true);
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
			requireSharedSalesQuantity(tenantId, item.getSkuId(), itemDTO.getRequestedQuantity());
            procurementLedger.manualShared(cartId, itemId, item.getSkuId(), userId, item.getRequestedQuantity(), itemDTO.getRequestedQuantity(), false);
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
        procurementLedger.manualShared(cartId, itemId, item.getSkuId(), userId, item.getRequestedQuantity(), 0, false);
		item.setStatus(SharedCartItem.ITEM_REMOVED);
		sharedCartItemMapper.updateById(item);
	}

	/** 历史复用已迁移至持久采购来源；旧命令不得直接写购物车。 */
	@Override
	public SharedCartReuseVO reuseFromHistory(String tenantId, String userId, String sourceCartId, SharedCartReuseDTO dto) {
		throw new ArynBusinessException("请在采购工作台复用历史清单并核对当前商品");
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
		if (isShoreCart(cart)) {
			ensureOrderableAddress(tenantId, cart);
		}
		else {
			ensureOrderableCall(tenantId, cart);
		}

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

	/**
	 * 岸上车提交前的地址可用性校验。
	 *
	 * <p>车的地址是创建时刻的选择，收集期内用户可能改删地址、运营可能收窄配送范围。
	 * 地址被删时无法自动顺延（不像船供车可换靠港），直接给出可读报错引导重新发起；
	 * 范围被收窄同理。订单主链路对 way=3 还会再校验一次，这里是提前给出车维度文案。
	 */
	private void ensureOrderableAddress(String tenantId, SharedCart cart) {
		// 地址归属发起人（建车时校验过）：确认人可能被改派，不能按确认人查地址
		UserAddress address = remoteUserAddressService.getById(cart.getUserAddressId(), cart.getOwnerUserId());
		if (address == null) {
			throw new ArynBusinessException("采购地址已被删除，请关闭本单后重新发起共享购物车");
		}
		if (!deliveryAreaService.isAddressInDeliveryArea(address.getProvinceCode(), address.getCityCode(),
				address.getAreaCode())) {
			throw new ArynBusinessException("采购地址已不在公司配送（岸上）范围内，请关闭本单后重新发起");
		}
	}

	/** 岸上企业车判定：cartType=2；存量数据 cartType 为空按船供处理。 */
	private boolean isShoreCart(SharedCart cart) {
		return SharedCart.CART_TYPE_SHORE.equals(cart.getCartType());
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
			vo.setCartType(StringUtils.hasText(cart.getCartType()) ? cart.getCartType() : SharedCart.CART_TYPE_SHIP);
			vo.setVesselId(cart.getVesselId());
			vo.setVesselCallId(cart.getVesselCallId());
			vo.setUserAddressId(cart.getUserAddressId());
			vo.setAddressSnapshot(cart.getAddressSnapshot());
			vo.setBuyerOrgId(cart.getBuyerOrgId());
			vo.setBuyerOrgName(cart.getBuyerOrgName());
			if (StringUtils.hasText(cart.getBuyerOrgId())) {
				BuyerOrg org = buyerOrgService.getOrg(tenantId, cart.getBuyerOrgId());
				vo.setBuyerOrgAuditStatus(org == null ? null : org.getAuditStatus());
			}
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
	public SharedCartJoinVO joinByShareToken(String tenantId, String userId, String shareToken) {
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
			// 幂等分支没有发生成员关系补建：本次是否「成为船员」的答案必须是 false，
			// 否则老成员每次点链接都会被重复告知「你已加入本船成员」。
			return toJoinVO(cart, tenantId, false);
		}

		// 自动补建船舶成员关系：船员扫码进群即获得该船的船员身份，无需后台配置。
		// 失败不阻断加入——Dubbo 不可用时仍允许参与本次采购，船舶成员校验在加购/下单环节再兜底。
		// 岸上企业车没有船舶维度，跳过补建（vesselMemberBound 恒 false，前端不提示「已加入本船」）。
		boolean member = false;
		if (StringUtils.hasText(cart.getVesselId())) {
			try {
				member = remoteVesselService.isVesselMember(tenantId, cart.getVesselId(), userId);
			}
			catch (Exception e) {
				log.warn("校验船舶成员关系失败，继续加入共享购物车：cartId={} userId={}", cart.getId(), userId, e);
			}
		}
		// bound 记录**本次**是否真的补建了成员关系（原本就在船上 / 补建失败都为 false）：
		// 前端据此决定是否提示「已同时加入本船成员」，不能把「无需补建」说成「刚加入」。
		boolean bound = false;
		if (StringUtils.hasText(cart.getVesselId()) && !member) {
			try {
				bound = remoteVesselService.bindMemberByShare(tenantId, cart.getVesselId(), userId);
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
		log.info("用户[{}]凭分享令牌加入共享购物车[{}]，本次补建船舶成员={}", userId, cart.getId(), bound);
		return toJoinVO(cart, tenantId, bound);
	}

	/**
	 * 组装分享加入结果：只回传提示所需的购物车 ID / 船名 / 是否本次补建了成员关系。
	 *
	 * <p>船名走靠港展示快照（{@code getVesselCallSnapshot}），与列表/详情同一口径：
	 * 靠港已结束的历史单照样有名字；船舶域不可用时降级为 null，绝不阻断加入。
	 */
	private SharedCartJoinVO toJoinVO(SharedCart cart, String tenantId, boolean vesselMemberBound) {
		SharedCartJoinVO vo = new SharedCartJoinVO();
		vo.setCartId(cart.getId());
		vo.setVesselMemberBound(vesselMemberBound);
		vo.setVesselName(resolveVesselNameForJoin(tenantId, cart));
		return vo;
	}

	/**
	 * 取加入提示用的船名，查不到（未挂靠港 / 远程不可用 / 上下文缺失）返回 null。
	 */
	private String resolveVesselNameForJoin(String tenantId, SharedCart cart) {
		if (cart == null || !StringUtils.hasText(cart.getVesselCallId())) {
			return null;
		}
		try {
			VesselContextDTO snapshot = remoteVesselService.getVesselCallSnapshot(tenantId, cart.getVesselCallId());
			if (snapshot != null && StringUtils.hasText(snapshot.getVesselName())) {
				return snapshot.getVesselName();
			}
		}
		catch (Exception e) {
			log.warn("分享加入时查询船名失败，提示中省略船名：cartId={}", cart.getId(), e);
		}
		return null;
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

	/** 并入明细专用的购物车行锁读取：错误文案与 {@link #requireCart} 保持一致 */
	private SharedCart requireCartForUpdate(String tenantId, String cartId) {
		SharedCart cart = sharedCartMapper.selectForUpdate(tenantId, cartId);
		if (cart == null) {
			throw new ArynBusinessException("共享购物车不存在");
		}
		return cart;
	}

	private SharedCartItem requireItem(String tenantId, String cartId, String itemId) {
		SharedCartItem item = sharedCartItemMapper.selectOne(Wrappers.lambdaQuery(SharedCartItem.class)
				.eq(SharedCartItem::getTenantId, tenantId)
				.eq(SharedCartItem::getCartId, cartId)
				.eq(SharedCartItem::getId, itemId).last("FOR UPDATE"));
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
	 * 以自己名义落明细的成员必须已填写姓名（display_name，配送按人贴标签）。
	 *
	 * <p>C 端在详情页对未填姓名的成员强制弹层补填，这里是服务端真闸门：
	 * 浏览加购漏斗、船供直加、导入确认等绕开详情页预检的路径全部收口于此。
	 * 发起人也一样要填（create 只建成员行不填姓名）。成员行缺失按未填处理
	 * （前置的 requireCanEdit/requireReuseEditable 已保证行存在，这里是 fail-closed）。
	 */
	private void requireMemberDisplayName(SharedCart cart, String userId) {
		SharedCartMember member = sharedCartMemberMapper.selectOne(Wrappers.lambdaQuery(SharedCartMember.class)
				.eq(SharedCartMember::getTenantId, cart.getTenantId())
				.eq(SharedCartMember::getCartId, cart.getId())
				.eq(SharedCartMember::getUserId, userId));
		if (member == null || !StringUtils.hasText(member.getDisplayName())) {
			throw new ArynBusinessException(CommonConstants.SHARED_CART_NAME_REQUIRED,
					"请先填写姓名，配送时按姓名贴标签");
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
		boolean shoreCart = isShoreCart(cart);
		CreateOrderDTO createOrderDTO = new CreateOrderDTO();
		createOrderDTO.setUserId(cart.getConfirmerUserId() != null ? cart.getConfirmerUserId() : cart.getOwnerUserId());
		if (shoreCart) {
			// 岸上企业车：公司配送（岸上）送达收货地址，订单主链路按 userAddressId
			// 从地址簿回填收货人四段地址并校验配送范围（与个人 way=3 订单同一条路径）
			createOrderDTO.setDeliveryWay("3");
			createOrderDTO.setPurchaseScene("3");
			createOrderDTO.setUserAddressId(cart.getUserAddressId());
			// 主体快照随订单落库：P1 首单资格按 buyer_org_id 判，不能只看下单账号。
			// 历史岸上车没有主体，保持为空允许继续提交（不能因为补字段把旧车卡死）。
			createOrderDTO.setBuyerOrgId(cart.getBuyerOrgId());
			createOrderDTO.setBuyerOrgName(cart.getBuyerOrgName());
		}
		else {
			createOrderDTO.setDeliveryWay("4");
			createOrderDTO.setPurchaseScene("2");
			createOrderDTO.setVesselId(cart.getVesselId());
			createOrderDTO.setVesselCallId(cart.getVesselCallId());
		}
		createOrderDTO.setCreateWay("2");
		createOrderDTO.setRequestId("SC" + cart.getId());
		if (confirmDTO != null) {
			// 岸上车的收货人以地址簿为准（主链路会覆盖），这里不透传船边收货人
			if (!shoreCart) {
				createOrderDTO.setRecipientName(confirmDTO.getRecipientName());
				createOrderDTO.setRecipientPhone(confirmDTO.getRecipientPhone());
			}
			createOrderDTO.setAgentName(confirmDTO.getAgentName());
			createOrderDTO.setAgentPhone(confirmDTO.getAgentPhone());
			createOrderDTO.setRemark(confirmDTO.getRemark());
			// 货到付款透传给 createOrder 校验（船供车 way=4 / 岸上车 way=3 均允许 COD）：
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

	/** 旧同步新写已下线，历史读取继续保留。 */
	@Override
	public SharedCartImportVO previewImport(String tenantId, String userId, String cartId, String fileName,
			long fileSize, byte[] fileBytes) {
		throw new ArynBusinessException("请在采购工作台上传清单");
	}

	@Override
	public SharedCartImportVO previewChainImport(String tenantId, String userId, String cartId, String text) {
		throw new ArynBusinessException("请在采购工作台粘贴清单");
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

	@Override
	public SharedCartImportVO confirmImport(String tenantId, String userId, String cartId, String importId,
			SharedCartImportConfirmDTO dto) {
		throw new ArynBusinessException("历史报告只读，请在采购工作台重新解析并确认");
	}

	/** 明细归并键：归属人 + SKU + 归属姓名（空姓名归一为空串，与列默认值一致） */
	private static String cartItemKey(String userId, String skuId, String attributedName) {
		return userId + "|" + skuId + "|" + (StringUtils.hasText(attributedName) ? attributedName : "");
	}

	/**
	 * 按归属键（tenant + cart + user + sku + attributed_name，空姓名归一为空串）查现有明细行。
	 *
	 * <p>uk_shared_cart_item 只认这五列、不含 status，物理上同键只允许一行；
	 * 已移除（status=3）的行并不消失，仍占着键位。任何写入路径在 INSERT 前
	 * 都必须先走这里，否则第二次加购/复用直接撞唯一键 500。
	 */
	private SharedCartItem findCartItemByKey(String tenantId, String cartId, String userId, String skuId,
			String attributedName) {
		return sharedCartItemMapper.selectOne(Wrappers.lambdaQuery(SharedCartItem.class)
				.eq(SharedCartItem::getTenantId, tenantId)
				.eq(SharedCartItem::getCartId, cartId)
				.eq(SharedCartItem::getUserId, userId)
				.eq(SharedCartItem::getSkuId, skuId)
				.eq(SharedCartItem::getAttributedName, StringUtils.hasText(attributedName) ? attributedName : "").last("FOR UPDATE"));
	}

	/**
	 * 单条明细的写入入口：无行则新建，活动行累加数量，已移除行复活。
	 *
	 * <p>{@code accumulateOnActive} 为 false 时不累加、活动行原样返回（复用场景
	 * 由调用方先按「已有该商品」跳过，落到这里只处理空行与已移除行两种）。
	 */
	private SharedCartItem upsertOwnCartItem(String tenantId, String cartId, String userId, String attributedName,
			String spuId, String skuId, Integer quantity, String remark, boolean accumulateOnActive) {
		SharedCartItem item = findCartItemByKey(tenantId, cartId, userId, skuId, attributedName);
        int finalQuantity = quantity == null ? 0 : quantity;
        if (item != null && !SharedCartItem.ITEM_REMOVED.equals(item.getStatus()) && accumulateOnActive) {
            try { finalQuantity = Math.addExact(item.getRequestedQuantity() == null ? 0 : item.getRequestedQuantity(), finalQuantity); }
            catch (ArithmeticException exception) { throw new ArynBusinessException("数量超出上限"); }
        }
        requireSharedSalesQuantity(tenantId, skuId, finalQuantity);

		if (item == null) {
			SharedCartItem created = insertCartItem(tenantId, cartId, userId, attributedName, spuId, skuId, quantity,
					remark);
			sharedCartItemMapper.insert(created);
			return created;
		}
		if (SharedCartItem.ITEM_REMOVED.equals(item.getStatus())) {
			// 复活已移除明细：数量按本次加购的值重新计，旧行的数量是上次移除前的遗留
			resurrectCartItem(item, userId, attributedName, spuId, quantity, remark);
			return item;
		}
		if (accumulateOnActive) {
			// 与已有活动明细合并数量：同一种商品多次加购累加进成员申请量
			int currentRequested = item.getRequestedQuantity() == null ? 0 : item.getRequestedQuantity();
            procurementLedger.manualShared(cartId, item.getId(), item.getSkuId(), userId, currentRequested, finalQuantity, true);
			item.setRequestedQuantity(currentRequested + (quantity == null ? 0 : quantity));
			item.setUpdateTime(LocalDateTime.now());
			sharedCartItemMapper.updateById(item);
		}
		return item;
	}

    private void requireSharedSalesQuantity(String tenantId, String skuId, Integer quantity) {
        var matches = remoteReplenishImportMatchService.matchSkuIds(tenantId, List.of(skuId));
        var current = matches == null ? null : matches.stream().filter(match -> skuId.equals(match.getSkuId())).findFirst().orElse(null);
        requireSharedSalesQuantity(current, quantity);
    }
    private void requireSharedSalesQuantity(ReplenishImportMatchVO current, Integer quantity) {
        if (current == null || !"0".equals(current.getSkuStatus()) || !"1".equals(current.getSpuStatus())) throw new ArynBusinessException("商品已失效，请重新选品");
        if (!ReplenishMatchRules.quantityAcceptable(quantity, current.getMoq(), current.getStepQty())) throw new ArynBusinessException("数量不符合商品起订量或步长");
        if (current.getStock() == null || quantity > current.getStock()) throw new ArynBusinessException("数量超过当前库存");
    }

	/** 新建明细行的统一字段映射：归属姓名空值必须归一为空串（列 NOT NULL DEFAULT ''，键列之一） */
	private SharedCartItem insertCartItem(String tenantId, String cartId, String userId, String attributedName,
			String spuId, String skuId, Integer quantity, String remark) {
		SharedCartItem item = new SharedCartItem();
		item.setId(IdWorker.getIdStr());
		item.setCartId(cartId);
		item.setUserId(userId);
		item.setAttributedName(StringUtils.hasText(attributedName) ? attributedName : "");
		item.setSpuId(spuId);
		item.setSkuId(skuId);
		item.setRequestedQuantity(quantity);
		item.setStatus(SharedCartItem.ITEM_PENDING);
		item.setMemberRemark(remark);
		item.setTenantId(tenantId);
		item.setCreateTime(LocalDateTime.now());
		item.setDelFlag("0");
		return item;
	}

	/** 复活已移除明细：改回待确认并按新值覆盖，绝不另插一行（唯一键不含 status） */
	private void resurrectCartItem(SharedCartItem item, String userId, String attributedName, String spuId,
			Integer quantity, String remark) {
		item.setUserId(userId);
		item.setAttributedName(StringUtils.hasText(attributedName) ? attributedName : "");
		item.setSpuId(spuId);
		item.setStatus(SharedCartItem.ITEM_PENDING);
		item.setRequestedQuantity(quantity);
		if (StringUtils.hasText(remark)) {
			item.setMemberRemark(remark);
		}
		item.setUpdateTime(LocalDateTime.now());
		sharedCartItemMapper.updateById(item);
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


}
