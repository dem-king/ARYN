package com.aryn.cloud.order.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.constant.BuyerOrgConstants;
import com.aryn.cloud.order.api.dto.BuyerOrgApplyAuditDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgApplyDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgAuditDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgBatchAuditDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgInviteCodeDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgMemberDTO;
import com.aryn.cloud.order.api.dto.BuyerOrgSaveDTO;
import com.aryn.cloud.order.api.entity.BuyerOrg;
import com.aryn.cloud.order.api.entity.BuyerOrgApply;
import com.aryn.cloud.order.api.entity.BuyerOrgInviteCode;
import com.aryn.cloud.order.api.entity.BuyerOrgMember;
import com.aryn.cloud.order.api.vo.BuyerOrgApplyVO;
import com.aryn.cloud.order.api.vo.BuyerOrgMemberVO;
import com.aryn.cloud.order.api.vo.BuyerOrgVO;
import com.aryn.cloud.order.mapper.BuyerOrgApplyMapper;
import com.aryn.cloud.order.mapper.BuyerOrgInviteCodeMapper;
import com.aryn.cloud.order.mapper.BuyerOrgMapper;
import com.aryn.cloud.order.mapper.BuyerOrgMemberMapper;
import com.aryn.cloud.order.service.IBuyerOrgService;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 采购主体服务实现。
 *
 * <p>租户隔离：所有查询显式带 {@code tenant_id}，不依赖拦截器是否把新表登记完整；
 * 主体只能在同租户内被搜索/加入/代建。
 *
 * @author aryn
 * @since 2026/11/12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BuyerOrgServiceImpl implements IBuyerOrgService {

	private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

	private static final SecureRandom RANDOM = new SecureRandom();

	/** 单个用户最多创建的主体数：免审核后唯一还挡得住的批量造壳闸门 */
	private static final int MAX_ORGS_PER_USER = 3;

	private final BuyerOrgMapper buyerOrgMapper;

	private final BuyerOrgMemberMapper buyerOrgMemberMapper;

	private final BuyerOrgApplyMapper buyerOrgApplyMapper;

	private final BuyerOrgInviteCodeMapper buyerOrgInviteCodeMapper;

	private final RemoteMallUserService remoteMallUserService;

	// ==================================================================
	// C 端
	// ==================================================================

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BuyerOrgApply apply(String tenantId, String userId, BuyerOrgApplyDTO dto) {
		if (BuyerOrgConstants.APPLY_TYPE_CREATE.equals(dto.getApplyType())) {
			return applyCreate(tenantId, userId, dto);
		}
		if (BuyerOrgConstants.APPLY_TYPE_JOIN.equals(dto.getApplyType())) {
			return applyJoin(tenantId, userId, dto);
		}
		throw new ArynBusinessException("不支持的申请类型");
	}

	private BuyerOrgApply applyCreate(String tenantId, String userId, BuyerOrgApplyDTO dto) {
		if (!hasText(dto.getOrgName())) {
			throw new ArynBusinessException("请填写工厂/企业名称");
		}
		// 免审核后主体可以随便建，但「一个人开一串壳」必须挡住：
		// 首单权益按主体算，无上限等于无限薅。上限与产品口径一致（最多 3 个）。
		long created = buyerOrgMapper.selectCount(Wrappers.lambdaQuery(BuyerOrg.class)
			.eq(BuyerOrg::getTenantId, tenantId)
			.eq(BuyerOrg::getOwnerUserId, userId));
		if (created >= MAX_ORGS_PER_USER) {
			throw new ArynBusinessException("每人最多创建 " + MAX_ORGS_PER_USER + " 个采购主体");
		}
		String creditCode = trimToNull(dto.getCreditCode());
		if (creditCode != null) {
			BuyerOrg existing = buyerOrgMapper.selectOne(Wrappers.lambdaQuery(BuyerOrg.class)
				.eq(BuyerOrg::getTenantId, tenantId)
				.eq(BuyerOrg::getCreditCode, creditCode)
				.last("LIMIT 1"));
			if (existing != null) {
				throw new ArynBusinessException("该信用代码对应的采购主体已存在，请搜索后申请加入");
			}
			long pending = buyerOrgApplyMapper.selectCount(Wrappers.lambdaQuery(BuyerOrgApply.class)
				.eq(BuyerOrgApply::getTenantId, tenantId)
				.eq(BuyerOrgApply::getUserId, userId)
				.eq(BuyerOrgApply::getApplyType, BuyerOrgConstants.APPLY_TYPE_CREATE)
				.eq(BuyerOrgApply::getStatus, BuyerOrgConstants.APPLY_STATUS_PENDING)
				.eq(BuyerOrgApply::getCreditCode, creditCode));
			if (pending > 0) {
				throw new ArynBusinessException("该主体的创建申请正在审核中，请勿重复提交");
			}
		}

		LocalDateTime now = LocalDateTime.now();
		BuyerOrg org = new BuyerOrg()
			.setOrgCode("BO" + IdWorker.getIdStr())
			.setOrgName(dto.getOrgName().trim())
			.setOrgType(normalizeOrgType(dto.getOrgType()))
			.setCreditCode(creditCode)
			.setLicenseUrl(trimToNull(dto.getLicenseUrl()))
			.setProvince(trimToNull(dto.getProvince()))
			.setCity(trimToNull(dto.getCity()))
			.setDistrict(trimToNull(dto.getDistrict()))
			.setAddress(trimToNull(dto.getAddress()))
			.setContactName(trimToNull(dto.getContactName()))
			.setContactPhone(trimToNull(dto.getContactPhone()))
			.setAuditStatus(BuyerOrgConstants.AUDIT_APPROVED)
			.setAuditRemark("自助创建，免审核")
			.setAuditBy("SYSTEM")
			.setAuditTime(now)
			.setOwnerUserId(userId)
			.setStatus(BuyerOrgConstants.STATUS_NORMAL)
			.setBindSource(BuyerOrgConstants.BIND_SOURCE_SELF_SERVICE)
			.setRemark(trimToNull(dto.getRemark()))
			.setTenantId(tenantId);
		buyerOrgMapper.insert(org);

		BuyerOrgApply apply = new BuyerOrgApply()
			.setApplyNo("BA" + IdWorker.getIdStr())
			.setUserId(userId)
			.setApplyType(BuyerOrgConstants.APPLY_TYPE_CREATE)
			.setBuyerOrgId(org.getId())
			.setOrgName(org.getOrgName())
			.setOrgType(org.getOrgType())
			.setCreditCode(creditCode)
			.setLicenseUrl(org.getLicenseUrl())
			.setProvince(org.getProvince())
			.setCity(org.getCity())
			.setDistrict(org.getDistrict())
			.setAddress(org.getAddress())
			.setContactName(org.getContactName())
			.setContactPhone(org.getContactPhone())
			.setApplyRole(BuyerOrgConstants.ROLE_OWNER)
			.setStatus(BuyerOrgConstants.APPLY_STATUS_APPROVED)
			.setAuditBy("SYSTEM")
			.setAuditTime(now)
			.setAuditRemark("自助创建，免审核")
			.setMatchedOrgId(org.getId())
			.setTenantId(tenantId);
		buyerOrgApplyMapper.insert(apply);

		upsertMember(tenantId, org.getId(), userId, BuyerOrgConstants.ROLE_OWNER, "1", "1",
			BuyerOrgConstants.BIND_SOURCE_SELF_SERVICE, apply.getId(), null, now);
		return apply;
	}

	private BuyerOrgApply applyJoin(String tenantId, String userId, BuyerOrgApplyDTO dto) {
		if (!hasText(dto.getBuyerOrgId())) {
			throw new ArynBusinessException("请选择要加入的采购主体");
		}
		BuyerOrg org = requireExistingOrg(tenantId, dto.getBuyerOrgId());
		if (BuyerOrgConstants.AUDIT_DISABLED.equals(org.getAuditStatus())
			|| BuyerOrgConstants.AUDIT_REJECTED.equals(org.getAuditStatus())) {
			throw new ArynBusinessException("该采购主体当前不可加入");
		}
		if (BuyerOrgConstants.STATUS_DISABLED.equals(org.getStatus())) {
			throw new ArynBusinessException("该采购主体已停用");
		}
		BuyerOrgMember member = findMember(tenantId, org.getId(), userId);
		if (member != null && BuyerOrgConstants.MEMBER_STATUS_NORMAL.equals(member.getStatus())) {
			throw new ArynBusinessException("您已是该采购主体成员");
		}
		long pending = buyerOrgApplyMapper.selectCount(Wrappers.lambdaQuery(BuyerOrgApply.class)
			.eq(BuyerOrgApply::getTenantId, tenantId)
			.eq(BuyerOrgApply::getUserId, userId)
			.eq(BuyerOrgApply::getApplyType, BuyerOrgConstants.APPLY_TYPE_JOIN)
			.eq(BuyerOrgApply::getBuyerOrgId, org.getId())
			.eq(BuyerOrgApply::getStatus, BuyerOrgConstants.APPLY_STATUS_PENDING));
		if (pending > 0) {
			throw new ArynBusinessException("加入申请正在审核中，请勿重复提交");
		}

		// 免审核加入：默认采购员。厂长只能由创建者担任，加入通道一律不授予；
		// can_confirm 固定 0——能加购改数但不能提交整单，这是免审下防止冒用他人主体下单的闸门。
		String joinRole = normalizeRole(dto.getApplyRole());
		if (BuyerOrgConstants.ROLE_OWNER.equals(joinRole)) {
			joinRole = BuyerOrgConstants.ROLE_BUYER;
		}
		String canEdit = BuyerOrgConstants.ROLE_VIEWER.equals(joinRole) ? "0" : "1";
		LocalDateTime now = LocalDateTime.now();

		BuyerOrgApply apply = new BuyerOrgApply()
			.setApplyNo("BA" + IdWorker.getIdStr())
			.setUserId(userId)
			.setApplyType(BuyerOrgConstants.APPLY_TYPE_JOIN)
			.setBuyerOrgId(org.getId())
			.setOrgName(org.getOrgName())
			.setOrgType(org.getOrgType())
			.setCreditCode(org.getCreditCode())
			.setLicenseUrl(org.getLicenseUrl())
			.setProvince(hasText(dto.getProvince()) ? dto.getProvince() : org.getProvince())
			.setCity(hasText(dto.getCity()) ? dto.getCity() : org.getCity())
			.setDistrict(hasText(dto.getDistrict()) ? dto.getDistrict() : org.getDistrict())
			.setAddress(hasText(dto.getAddress()) ? dto.getAddress() : org.getAddress())
			.setContactName(trimToNull(dto.getContactName()))
			.setContactPhone(trimToNull(dto.getContactPhone()))
			.setApplyRole(joinRole)
			.setStatus(BuyerOrgConstants.APPLY_STATUS_APPROVED)
			.setAuditBy("SYSTEM")
			.setAuditTime(now)
			.setAuditRemark("自助加入，免审核")
			.setMatchedOrgId(org.getId())
			.setTenantId(tenantId);
		buyerOrgApplyMapper.insert(apply);
		upsertMember(tenantId, org.getId(), userId, joinRole, canEdit, "0",
			BuyerOrgConstants.BIND_SOURCE_SELF_SERVICE, apply.getId(), null, now);
		return apply;
	}

	@Override
	public List<BuyerOrgVO> listMyOrgs(String tenantId, String userId) {
		List<BuyerOrgMember> members = buyerOrgMemberMapper.selectList(Wrappers.lambdaQuery(BuyerOrgMember.class)
			.eq(BuyerOrgMember::getTenantId, tenantId)
			.eq(BuyerOrgMember::getUserId, userId)
			.eq(BuyerOrgMember::getStatus, BuyerOrgConstants.MEMBER_STATUS_NORMAL)
			.orderByDesc(BuyerOrgMember::getJoinTime)
			.orderByDesc(BuyerOrgMember::getCreateTime));
		if (members.isEmpty()) {
			return Collections.emptyList();
		}
		List<String> orgIds = members.stream().map(BuyerOrgMember::getBuyerOrgId).distinct().toList();
		List<BuyerOrg> orgs = buyerOrgMapper.selectList(Wrappers.lambdaQuery(BuyerOrg.class)
			.eq(BuyerOrg::getTenantId, tenantId)
			.in(BuyerOrg::getId, orgIds));
		Map<String, BuyerOrg> orgMap = orgs.stream()
			.collect(Collectors.toMap(BuyerOrg::getId, org -> org, (a, b) -> a));
		List<BuyerOrgVO> result = new ArrayList<>(members.size());
		for (BuyerOrgMember member : members) {
			BuyerOrg org = orgMap.get(member.getBuyerOrgId());
			if (org == null) {
				continue;
			}
			result.add(toOrgVO(org, member, countMembers(tenantId, org.getId())));
		}
	return result;
	}

	@Override
	public List<BuyerOrgVO> search(String tenantId, String userId, String keyword) {
		String kw = trimToNull(keyword);
		if (kw == null) {
			return Collections.emptyList();
		}
		List<BuyerOrg> orgs = buyerOrgMapper.selectList(Wrappers.lambdaQuery(BuyerOrg.class)
			.eq(BuyerOrg::getTenantId, tenantId)
			.eq(BuyerOrg::getStatus, BuyerOrgConstants.STATUS_NORMAL)
			// 免审核后新建主体落库即通过；这里兼容历史待审核主体，避免老数据搜不到、加不进去
			.in(BuyerOrg::getAuditStatus, BuyerOrgConstants.AUDIT_PENDING, BuyerOrgConstants.AUDIT_APPROVED)
			.and(w -> w.like(BuyerOrg::getOrgName, kw).or().like(BuyerOrg::getCreditCode, kw))
			.orderByDesc(BuyerOrg::getCreateTime)
			.last("LIMIT 20"));
		List<BuyerOrgVO> result = new ArrayList<>(orgs.size());
		for (BuyerOrg org : orgs) {
			BuyerOrgMember member = findMember(tenantId, org.getId(), userId);
			BuyerOrgApply pendingApply = buyerOrgApplyMapper.selectOne(Wrappers.lambdaQuery(BuyerOrgApply.class)
				.eq(BuyerOrgApply::getTenantId, tenantId)
				.eq(BuyerOrgApply::getUserId, userId)
				.eq(BuyerOrgApply::getBuyerOrgId, org.getId())
				.eq(BuyerOrgApply::getStatus, BuyerOrgConstants.APPLY_STATUS_PENDING)
				.orderByDesc(BuyerOrgApply::getCreateTime)
				.last("LIMIT 1"));
			BuyerOrgVO vo = toOrgVO(org, member, countMembers(tenantId, org.getId()));
			vo.setMyApplyStatus(pendingApply == null ? null : pendingApply.getStatus());
			result.add(vo);
		}
		return result;
	}

	@Override
	public List<BuyerOrgApplyVO> listMyApplies(String tenantId, String userId) {
		List<BuyerOrgApply> applies = buyerOrgApplyMapper.selectList(Wrappers.lambdaQuery(BuyerOrgApply.class)
			.eq(BuyerOrgApply::getTenantId, tenantId)
			.eq(BuyerOrgApply::getUserId, userId)
			.orderByDesc(BuyerOrgApply::getCreateTime)
			.last("LIMIT 50"));
		return applies.stream().map(apply -> toApplyVO(apply, null)).toList();
	}

	@Override
	public BuyerOrgVO getDetail(String tenantId, String userId, String orgId) {
		BuyerOrg org = requireExistingOrg(tenantId, orgId);
		BuyerOrgMember member = requireMember(tenantId, org.getId(), userId);
		return toOrgVO(org, member, countMembers(tenantId, org.getId()));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BuyerOrgMemberVO joinByInviteCode(String tenantId, String userId, String code) {
		String normalized = trimToNull(code);
		if (normalized == null) {
			throw new ArynBusinessException("请输入邀请码");
		}
		BuyerOrgInviteCode invite = buyerOrgInviteCodeMapper.selectOne(Wrappers.lambdaQuery(BuyerOrgInviteCode.class)
			.eq(BuyerOrgInviteCode::getTenantId, tenantId)
			.eq(BuyerOrgInviteCode::getCode, normalized.toUpperCase())
			.last("LIMIT 1"));
		if (invite == null) {
			throw new ArynBusinessException("邀请码不存在");
		}
		if (!BuyerOrgConstants.INVITE_CODE_STATUS_ACTIVE.equals(invite.getStatus())) {
			throw new ArynBusinessException("邀请码已失效");
		}
		if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
			buyerOrgInviteCodeMapper.update(null, Wrappers.lambdaUpdate(BuyerOrgInviteCode.class)
				.eq(BuyerOrgInviteCode::getId, invite.getId())
				.set(BuyerOrgInviteCode::getStatus, BuyerOrgConstants.INVITE_CODE_STATUS_INACTIVE));
			throw new ArynBusinessException("邀请码已过期");
		}
		if (invite.getMaxUses() != null && invite.getMaxUses() > 0
			&& invite.getUsedCount() != null && invite.getUsedCount() >= invite.getMaxUses()) {
			throw new ArynBusinessException("邀请码使用次数已达上限");
		}
		BuyerOrg org = requireExistingOrg(tenantId, invite.getBuyerOrgId());
		if (!BuyerOrgConstants.STATUS_NORMAL.equals(org.getStatus())
			|| !BuyerOrgConstants.usableAuditStatus(org.getAuditStatus())) {
			throw new ArynBusinessException("该采购主体当前不可加入");
		}
		BuyerOrgMember existing = findMember(tenantId, org.getId(), userId);
		if (existing != null && BuyerOrgConstants.MEMBER_STATUS_NORMAL.equals(existing.getStatus())) {
			throw new ArynBusinessException("您已是该采购主体成员");
		}

		LocalDateTime now = LocalDateTime.now();
		BuyerOrgMember member = upsertMember(tenantId, org.getId(), userId, BuyerOrgConstants.ROLE_BUYER,
			"1", "1", BuyerOrgConstants.BIND_SOURCE_INVITE_CODE, null, null, now);
		buyerOrgInviteCodeMapper.update(null, Wrappers.lambdaUpdate(BuyerOrgInviteCode.class)
			.eq(BuyerOrgInviteCode::getId, invite.getId())
			.setSql("used_count = used_count + 1"));
		return toMemberVO(member, loadUsers(List.of(userId)).get(userId));
	}

	@Override
	public List<BuyerOrgMemberVO> listMembers(String tenantId, String userId, String orgId) {
		requireMember(tenantId, orgId, userId);
		return listMembersInternal(tenantId, orgId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BuyerOrgInviteCode createInviteCode(String tenantId, String userId, String orgId,
			BuyerOrgInviteCodeDTO dto) {
		BuyerOrg org = requireExistingOrg(tenantId, orgId);
		requireOwner(tenantId, org, userId);
		if (!BuyerOrgConstants.STATUS_NORMAL.equals(org.getStatus())
			|| !BuyerOrgConstants.usableAuditStatus(org.getAuditStatus())) {
			throw new ArynBusinessException("该采购主体当前不可生成邀请码");
		}
		// 同主体只保留一个有效码，旧码置为失效
		buyerOrgInviteCodeMapper.update(null, Wrappers.lambdaUpdate(BuyerOrgInviteCode.class)
			.eq(BuyerOrgInviteCode::getTenantId, tenantId)
			.eq(BuyerOrgInviteCode::getBuyerOrgId, org.getId())
			.eq(BuyerOrgInviteCode::getStatus, BuyerOrgConstants.INVITE_CODE_STATUS_ACTIVE)
			.set(BuyerOrgInviteCode::getStatus, BuyerOrgConstants.INVITE_CODE_STATUS_INACTIVE)
			.set(BuyerOrgInviteCode::getUpdateTime, LocalDateTime.now()));

		int expireHours = dto == null || dto.getExpireHours() == null
			? BuyerOrgConstants.INVITE_CODE_DEFAULT_EXPIRE_HOURS
			: Math.min(Math.max(dto.getExpireHours(), 1), BuyerOrgConstants.INVITE_CODE_MAX_EXPIRE_HOURS);
		int maxUses = dto == null || dto.getMaxUses() == null ? 0 : Math.max(dto.getMaxUses(), 0);
		BuyerOrgInviteCode invite = new BuyerOrgInviteCode()
			.setBuyerOrgId(org.getId())
			.setCode(generateUniqueCode(tenantId))
			.setExpiresAt(LocalDateTime.now().plusHours(expireHours))
			.setMaxUses(maxUses)
			.setUsedCount(0)
			.setStatus(BuyerOrgConstants.INVITE_CODE_STATUS_ACTIVE)
			.setRemark(dto == null ? null : trimToNull(dto.getRemark()))
			.setTenantId(tenantId);
		buyerOrgInviteCodeMapper.insert(invite);
		return invite;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void leave(String tenantId, String userId, String orgId) {
		BuyerOrg org = requireExistingOrg(tenantId, orgId);
		BuyerOrgMember member = requireMember(tenantId, org.getId(), userId);
		if (userIsOwner(org, member, userId)) {
			throw new ArynBusinessException("厂长/管理员不能退出主体，请先转让管理权限");
		}
		buyerOrgMemberMapper.update(null, Wrappers.lambdaUpdate(BuyerOrgMember.class)
			.eq(BuyerOrgMember::getId, member.getId())
			.set(BuyerOrgMember::getStatus, BuyerOrgConstants.MEMBER_STATUS_REMOVED)
			.set(BuyerOrgMember::getLeaveTime, LocalDateTime.now()));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void removeMember(String tenantId, String userId, String orgId, String memberUserId) {
		BuyerOrg org = requireExistingOrg(tenantId, orgId);
		requireOwner(tenantId, org, userId);
		if (!hasText(memberUserId)) {
			throw new ArynBusinessException("请选择要移除的成员");
		}
		BuyerOrgMember target = requireMember(tenantId, org.getId(), memberUserId);
		if (userIsOwner(org, target, memberUserId)) {
			throw new ArynBusinessException("不能移除厂长/管理员");
		}
		buyerOrgMemberMapper.update(null, Wrappers.lambdaUpdate(BuyerOrgMember.class)
			.eq(BuyerOrgMember::getId, target.getId())
			.set(BuyerOrgMember::getStatus, BuyerOrgConstants.MEMBER_STATUS_REMOVED)
			.set(BuyerOrgMember::getLeaveTime, LocalDateTime.now()));
	}

	// ==================================================================
	// 管理端
	// ==================================================================

	@Override
	public IPage<BuyerOrg> adminPage(Page<BuyerOrg> page, String tenantId, String orgName, String creditCode,
			String orgType, String auditStatus, String status) {
		return buyerOrgMapper.selectPage(page, Wrappers.lambdaQuery(BuyerOrg.class)
			.eq(BuyerOrg::getTenantId, tenantId)
			.like(hasText(orgName), BuyerOrg::getOrgName, orgName)
			.like(hasText(creditCode), BuyerOrg::getCreditCode, creditCode)
			.eq(hasText(orgType), BuyerOrg::getOrgType, orgType)
			.eq(hasText(auditStatus), BuyerOrg::getAuditStatus, auditStatus)
			.eq(hasText(status), BuyerOrg::getStatus, status)
			.orderByDesc(BuyerOrg::getCreateTime));
	}

	@Override
	public BuyerOrg adminGet(String tenantId, String orgId) {
		return requireExistingOrg(tenantId, orgId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BuyerOrg adminCreate(String tenantId, String operator, BuyerOrgSaveDTO dto) {
		String creditCode = trimToNull(dto.getCreditCode());
		if (creditCode != null) {
			BuyerOrg existing = buyerOrgMapper.selectOne(Wrappers.lambdaQuery(BuyerOrg.class)
				.eq(BuyerOrg::getTenantId, tenantId)
				.eq(BuyerOrg::getCreditCode, creditCode)
				.last("LIMIT 1"));
			if (existing != null) {
				throw new ArynBusinessException("该信用代码已存在");
			}
		}
		BuyerOrg org = new BuyerOrg()
			.setOrgCode("BO" + IdWorker.getIdStr())
			.setOrgName(dto.getOrgName().trim())
			.setOrgType(normalizeOrgType(dto.getOrgType()))
			.setCreditCode(creditCode)
			.setLicenseUrl(trimToNull(dto.getLicenseUrl()))
			.setProvince(trimToNull(dto.getProvince()))
			.setCity(trimToNull(dto.getCity()))
			.setDistrict(trimToNull(dto.getDistrict()))
			.setAddress(trimToNull(dto.getAddress()))
			.setContactName(trimToNull(dto.getContactName()))
			.setContactPhone(trimToNull(dto.getContactPhone()))
			.setAuditStatus(BuyerOrgConstants.AUDIT_APPROVED)
			.setAuditBy(operator)
			.setAuditTime(LocalDateTime.now())
			.setAuditRemark("运营代建")
			.setOwnerUserId(trimToNull(dto.getOwnerUserId()))
			.setStatus(BuyerOrgConstants.STATUS_NORMAL)
			.setBindSource(BuyerOrgConstants.BIND_SOURCE_ADMIN_ADD)
			.setRemark(trimToNull(dto.getRemark()))
			.setTenantId(tenantId);
		buyerOrgMapper.insert(org);
		if (hasText(org.getOwnerUserId())) {
			upsertMember(tenantId, org.getId(), org.getOwnerUserId(), BuyerOrgConstants.ROLE_OWNER,
				"1", "1", BuyerOrgConstants.BIND_SOURCE_ADMIN_ADD, null, null, LocalDateTime.now());
		}
		return org;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void adminUpdate(String tenantId, String operator, String orgId, BuyerOrgSaveDTO dto) {
		BuyerOrg org = requireExistingOrg(tenantId, orgId);
		String creditCode = trimToNull(dto.getCreditCode());
		if (creditCode != null) {
			BuyerOrg existing = buyerOrgMapper.selectOne(Wrappers.lambdaQuery(BuyerOrg.class)
				.eq(BuyerOrg::getTenantId, tenantId)
				.eq(BuyerOrg::getCreditCode, creditCode)
				.ne(BuyerOrg::getId, org.getId())
				.last("LIMIT 1"));
			if (existing != null) {
				throw new ArynBusinessException("该信用代码已被其他主体使用");
			}
		}
		org.setOrgName(dto.getOrgName().trim())
			.setOrgType(normalizeOrgType(dto.getOrgType()))
			.setCreditCode(creditCode)
			.setLicenseUrl(trimToNull(dto.getLicenseUrl()))
			.setProvince(trimToNull(dto.getProvince()))
			.setCity(trimToNull(dto.getCity()))
			.setDistrict(trimToNull(dto.getDistrict()))
			.setAddress(trimToNull(dto.getAddress()))
			.setContactName(trimToNull(dto.getContactName()))
			.setContactPhone(trimToNull(dto.getContactPhone()))
			.setRemark(trimToNull(dto.getRemark()))
			.setUpdateBy(operator);
		String newOwner = trimToNull(dto.getOwnerUserId());
		if (newOwner != null && !Objects.equals(newOwner, org.getOwnerUserId())) {
			org.setOwnerUserId(newOwner);
			upsertMember(tenantId, org.getId(), newOwner, BuyerOrgConstants.ROLE_OWNER,
				"1", "1", BuyerOrgConstants.BIND_SOURCE_ADMIN_ADD, null, null, LocalDateTime.now());
		}
		buyerOrgMapper.updateById(org);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void adminAudit(String tenantId, String operator, String orgId, BuyerOrgAuditDTO dto) {
		String auditStatus = dto.getAuditStatus();
		if (!BuyerOrgConstants.AUDIT_APPROVED.equals(auditStatus)
			&& !BuyerOrgConstants.AUDIT_REJECTED.equals(auditStatus)
			&& !BuyerOrgConstants.AUDIT_DISABLED.equals(auditStatus)) {
			throw new ArynBusinessException("审核状态不合法");
		}
		BuyerOrg org = requireExistingOrg(tenantId, orgId);
		org.setAuditStatus(auditStatus)
			.setAuditBy(operator)
			.setAuditTime(LocalDateTime.now())
			.setAuditRemark(trimToNull(dto.getAuditRemark()))
			.setUpdateBy(operator);
		if (BuyerOrgConstants.AUDIT_DISABLED.equals(auditStatus)) {
			org.setStatus(BuyerOrgConstants.STATUS_DISABLED);
		} else if (BuyerOrgConstants.AUDIT_APPROVED.equals(auditStatus)) {
			org.setStatus(BuyerOrgConstants.STATUS_NORMAL);
		}
		buyerOrgMapper.updateById(org);

		// 同步该主体下待审核申请，避免申请列表停在「待审核」
		String applyStatus = BuyerOrgConstants.AUDIT_APPROVED.equals(auditStatus)
			? BuyerOrgConstants.APPLY_STATUS_APPROVED : BuyerOrgConstants.APPLY_STATUS_REJECTED;
		buyerOrgApplyMapper.update(null, Wrappers.lambdaUpdate(BuyerOrgApply.class)
			.eq(BuyerOrgApply::getTenantId, tenantId)
			.eq(BuyerOrgApply::getMatchedOrgId, org.getId())
			.eq(BuyerOrgApply::getStatus, BuyerOrgConstants.APPLY_STATUS_PENDING)
			.set(BuyerOrgApply::getStatus, applyStatus)
			.set(BuyerOrgApply::getAuditBy, operator)
			.set(BuyerOrgApply::getAuditTime, LocalDateTime.now())
			.set(BuyerOrgApply::getAuditRemark, trimToNull(dto.getAuditRemark())));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public int adminBatchAudit(String tenantId, String operator, BuyerOrgBatchAuditDTO dto) {
		int handled = 0;
		for (String orgId : dto.getIds()) {
			BuyerOrgAuditDTO single = new BuyerOrgAuditDTO();
			single.setAuditStatus(dto.getAuditStatus());
			single.setAuditRemark(dto.getAuditRemark());
			adminAudit(tenantId, operator, orgId, single);
			handled++;
		}
		return handled;
	}

	@Override
	public List<BuyerOrgMemberVO> adminListMembers(String tenantId, String orgId) {
		requireExistingOrg(tenantId, orgId);
		return listMembersInternal(tenantId, orgId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public BuyerOrgMemberVO adminAddMember(String tenantId, String operator, String orgId, BuyerOrgMemberDTO dto) {
		BuyerOrg org = requireExistingOrg(tenantId, orgId);
		validateUserInTenant(dto.getUserId(), tenantId);
		String role = normalizeRole(dto.getMemberRole());
		String canEdit = defaultFlag(dto.getCanEdit(), BuyerOrgConstants.ROLE_VIEWER.equals(role) ? "0" : "1");
		String canConfirm = defaultFlag(dto.getCanConfirm(), BuyerOrgConstants.ROLE_VIEWER.equals(role) ? "0" : "1");
		BuyerOrgMember member = upsertMember(tenantId, org.getId(), dto.getUserId(), role, canEdit, canConfirm,
			BuyerOrgConstants.BIND_SOURCE_MEMBER_ADD, null, trimToNull(dto.getRemark()), LocalDateTime.now());
		return toMemberVO(member, loadUsers(List.of(dto.getUserId())).get(dto.getUserId()));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void adminRemoveMember(String tenantId, String operator, String orgId, String memberUserId) {
		BuyerOrg org = requireExistingOrg(tenantId, orgId);
		BuyerOrgMember target = requireMember(tenantId, org.getId(), memberUserId);
		if (userIsOwner(org, target, memberUserId)) {
			throw new ArynBusinessException("不能移除厂长/管理员");
		}
		buyerOrgMemberMapper.update(null, Wrappers.lambdaUpdate(BuyerOrgMember.class)
			.eq(BuyerOrgMember::getId, target.getId())
			.set(BuyerOrgMember::getStatus, BuyerOrgConstants.MEMBER_STATUS_REMOVED)
			.set(BuyerOrgMember::getLeaveTime, LocalDateTime.now()));
	}

	@Override
	public IPage<BuyerOrgApplyVO> adminApplyPage(Page<BuyerOrgApply> page, String tenantId, String status,
			String orgName, String applyType) {
		IPage<BuyerOrgApply> applyPage = buyerOrgApplyMapper.selectPage(page,
			Wrappers.lambdaQuery(BuyerOrgApply.class)
				.eq(BuyerOrgApply::getTenantId, tenantId)
				.eq(hasText(status), BuyerOrgApply::getStatus, status)
				.like(hasText(orgName), BuyerOrgApply::getOrgName, orgName)
				.eq(hasText(applyType), BuyerOrgApply::getApplyType, applyType)
				// 待审核(1)排最前，其余按申请时间倒序
				.orderByAsc(BuyerOrgApply::getStatus)
				.orderByDesc(BuyerOrgApply::getCreateTime));
		Map<String, UserInfoVO> users = loadUsers(applyPage.getRecords().stream()
			.map(BuyerOrgApply::getUserId).filter(Objects::nonNull).distinct().toList());
		return applyPage.convert(apply -> toApplyVO(apply, users.get(apply.getUserId())));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void adminAuditApply(String tenantId, String operator, String applyId, BuyerOrgApplyAuditDTO dto) {
		if (dto == null || dto.getApproved() == null) {
			throw new ArynBusinessException("请选择审核结果");
		}
		BuyerOrgApply apply = buyerOrgApplyMapper.selectOne(Wrappers.lambdaQuery(BuyerOrgApply.class)
			.eq(BuyerOrgApply::getTenantId, tenantId)
			.eq(BuyerOrgApply::getId, applyId)
			.last("LIMIT 1"));
		if (apply == null) {
			throw new ArynBusinessException("申请单不存在");
		}
		if (!BuyerOrgConstants.APPLY_STATUS_PENDING.equals(apply.getStatus())) {
			throw new ArynBusinessException("该申请单已处理");
			}
		LocalDateTime now = LocalDateTime.now();
		if (Boolean.FALSE.equals(dto.getApproved())) {
			apply.setStatus(BuyerOrgConstants.APPLY_STATUS_REJECTED)
				.setAuditBy(operator)
				.setAuditTime(now)
				.setAuditRemark(trimToNull(dto.getAuditRemark()));
			buyerOrgApplyMapper.updateById(apply);
			BuyerOrg org = getOrg(tenantId, apply.getMatchedOrgId());
			if (org != null && BuyerOrgConstants.APPLY_TYPE_CREATE.equals(apply.getApplyType())) {
				org.setAuditStatus(BuyerOrgConstants.AUDIT_REJECTED)
					.setAuditBy(operator)
					.setAuditTime(now)
					.setAuditRemark(trimToNull(dto.getAuditRemark()));
				buyerOrgMapper.updateById(org);
			}
			return;
		}

		String targetOrgId = trimToNull(dto.getMatchedOrgId());
		if (targetOrgId == null) {
			targetOrgId = trimToNull(apply.getMatchedOrgId());
		}
		if (targetOrgId == null && BuyerOrgConstants.APPLY_TYPE_JOIN.equals(apply.getApplyType())) {
			targetOrgId = trimToNull(apply.getBuyerOrgId());
		}
		BuyerOrg org;
		if (targetOrgId == null && BuyerOrgConstants.APPLY_TYPE_CREATE.equals(apply.getApplyType())) {
			// 兼容历史/异常数据：申请单没有关联主体时补建一个已通过主体
			org = createOrgFromApply(tenantId, operator, apply, now);
			targetOrgId = org.getId();
		} else {
			org = requireExistingOrg(tenantId, targetOrgId);
			if (BuyerOrgConstants.APPLY_TYPE_CREATE.equals(apply.getApplyType())) {
				org.setAuditStatus(BuyerOrgConstants.AUDIT_APPROVED)
					.setAuditBy(operator)
					.setAuditTime(now)
					.setAuditRemark(trimToNull(dto.getAuditRemark()))
					.setStatus(BuyerOrgConstants.STATUS_NORMAL);
				buyerOrgMapper.updateById(org);
			}
		}

		String role = BuyerOrgConstants.APPLY_TYPE_CREATE.equals(apply.getApplyType())
			? BuyerOrgConstants.ROLE_OWNER : normalizeRole(apply.getApplyRole());
		upsertMember(tenantId, org.getId(), apply.getUserId(), role, "1", "1",
			BuyerOrgConstants.BIND_SOURCE_APPLY_AUDIT, apply.getId(), null, now);
		apply.setStatus(BuyerOrgConstants.APPLY_STATUS_APPROVED)
			.setAuditBy(operator)
			.setAuditTime(now)
			.setAuditRemark(trimToNull(dto.getAuditRemark()))
			.setMatchedOrgId(org.getId());
		buyerOrgApplyMapper.updateById(apply);
	}

	// ==================================================================
	// 岸上车 / 订单内部校验
	// ==================================================================

	@Override
	public BuyerOrg requireUsableForCart(String tenantId, String userId, String buyerOrgId) {
		BuyerOrg org = requireUsableOrg(tenantId, buyerOrgId);
		BuyerOrgMember member = requireMember(tenantId, org.getId(), userId);
		if (!"1".equals(member.getCanEdit())) {
			throw new ArynBusinessException("当前成员角色无建车权限");
		}
		return org;
	}

	@Override
	public BuyerOrg requireForOrder(String tenantId, String userId, String buyerOrgId) {
		BuyerOrg org = requireUsableOrg(tenantId, buyerOrgId);
		requireMember(tenantId, org.getId(), userId);
		return org;
	}

	@Override
	public BuyerOrg getOrg(String tenantId, String buyerOrgId) {
		if (!hasText(buyerOrgId)) {
			return null;
		}
		return buyerOrgMapper.selectOne(Wrappers.lambdaQuery(BuyerOrg.class)
			.eq(BuyerOrg::getTenantId, tenantId)
			.eq(BuyerOrg::getId, buyerOrgId)
			.last("LIMIT 1"));
	}

	// ==================================================================
	// 内部工具
	// ==================================================================

	private BuyerOrg requireUsableOrg(String tenantId, String buyerOrgId) {
		BuyerOrg org = requireExistingOrg(tenantId, buyerOrgId);
		if (!BuyerOrgConstants.STATUS_NORMAL.equals(org.getStatus())) {
			throw new ArynBusinessException("采购主体已停用");
		}
		if (BuyerOrgConstants.AUDIT_DISABLED.equals(org.getAuditStatus())) {
			throw new ArynBusinessException("采购主体已停用");
		}
		if (BuyerOrgConstants.AUDIT_REJECTED.equals(org.getAuditStatus())) {
			throw new ArynBusinessException("采购主体未通过审核，请重新申请");
		}
		if (!BuyerOrgConstants.usableAuditStatus(org.getAuditStatus())) {
			throw new ArynBusinessException("采购主体状态不可用");
		}
		return org;
	}

	private BuyerOrg requireExistingOrg(String tenantId, String orgId) {
		if (!hasText(orgId)) {
			throw new ArynBusinessException("请选择采购主体");
		}
		BuyerOrg org = getOrg(tenantId, orgId);
		if (org == null) {
			throw new ArynBusinessException("采购主体不存在");
		}
		return org;
	}

	private BuyerOrgMember requireMember(String tenantId, String orgId, String userId) {
		BuyerOrgMember member = findMember(tenantId, orgId, userId);
		if (member == null || !BuyerOrgConstants.MEMBER_STATUS_NORMAL.equals(member.getStatus())) {
			throw new ArynBusinessException("您不是该采购主体成员");
		}
		return member;
	}

	private void requireOwner(String tenantId, BuyerOrg org, String userId) {
		BuyerOrgMember member = requireMember(tenantId, org.getId(), userId);
		if (!userIsOwner(org, member, userId)) {
			throw new ArynBusinessException("仅厂长/管理员可操作");
		}
	}

	private boolean userIsOwner(BuyerOrg org, BuyerOrgMember member, String userId) {
		return Objects.equals(org.getOwnerUserId(), userId)
			|| (member != null && BuyerOrgConstants.ROLE_OWNER.equals(member.getMemberRole()));
	}

	private BuyerOrgMember findMember(String tenantId, String orgId, String userId) {
		if (!hasText(userId)) {
			return null;
		}
		return buyerOrgMemberMapper.selectOne(Wrappers.lambdaQuery(BuyerOrgMember.class)
			.eq(BuyerOrgMember::getTenantId, tenantId)
			.eq(BuyerOrgMember::getBuyerOrgId, orgId)
			.eq(BuyerOrgMember::getUserId, userId)
			.last("LIMIT 1"));
	}

	/**
	 * 插入或复活成员行：唯一键不含 del_flag/status，退出后再次加入必须复用原行。
	 */
	private BuyerOrgMember upsertMember(String tenantId, String orgId, String userId, String role, String canEdit,
			String canConfirm, String bindSource, String applyId, String remark, LocalDateTime joinTime) {
		BuyerOrgMember existing = findMember(tenantId, orgId, userId);
		if (existing != null) {
			existing.setMemberRole(role)
				.setCanEdit(canEdit)
				.setCanConfirm(canConfirm)
				.setStatus(BuyerOrgConstants.MEMBER_STATUS_NORMAL)
				.setBindSource(bindSource)
				.setApplyId(applyId)
				.setJoinTime(joinTime)
				.setLeaveTime(null)
				.setRemark(remark);
			buyerOrgMemberMapper.updateById(existing);
			return existing;
		}
		BuyerOrgMember member = new BuyerOrgMember()
			.setBuyerOrgId(orgId)
			.setUserId(userId)
			.setMemberRole(role)
			.setCanEdit(canEdit)
			.setCanConfirm(canConfirm)
			.setStatus(BuyerOrgConstants.MEMBER_STATUS_NORMAL)
			.setBindSource(bindSource)
			.setApplyId(applyId)
			.setJoinTime(joinTime)
			.setRemark(remark)
			.setTenantId(tenantId);
		buyerOrgMemberMapper.insert(member);
		return member;
	}

	private List<BuyerOrgMemberVO> listMembersInternal(String tenantId, String orgId) {
		List<BuyerOrgMember> members = buyerOrgMemberMapper.selectList(Wrappers.lambdaQuery(BuyerOrgMember.class)
			.eq(BuyerOrgMember::getTenantId, tenantId)
			.eq(BuyerOrgMember::getBuyerOrgId, orgId)
			.eq(BuyerOrgMember::getStatus, BuyerOrgConstants.MEMBER_STATUS_NORMAL)
			.orderByAsc(BuyerOrgMember::getMemberRole)
			.orderByAsc(BuyerOrgMember::getJoinTime));
		Map<String, UserInfoVO> users = loadUsers(members.stream()
			.map(BuyerOrgMember::getUserId).filter(Objects::nonNull).distinct().toList());
		return members.stream().map(member -> toMemberVO(member, users.get(member.getUserId()))).toList();
	}

	private long countMembers(String tenantId, String orgId) {
		return buyerOrgMemberMapper.selectCount(Wrappers.lambdaQuery(BuyerOrgMember.class)
			.eq(BuyerOrgMember::getTenantId, tenantId)
			.eq(BuyerOrgMember::getBuyerOrgId, orgId)
			.eq(BuyerOrgMember::getStatus, BuyerOrgConstants.MEMBER_STATUS_NORMAL));
	}

	private BuyerOrg createOrgFromApply(String tenantId, String operator, BuyerOrgApply apply, LocalDateTime now) {
		BuyerOrg org = new BuyerOrg()
			.setOrgCode("BO" + IdWorker.getIdStr())
			.setOrgName(apply.getOrgName())
			.setOrgType(normalizeOrgType(apply.getOrgType()))
			.setCreditCode(trimToNull(apply.getCreditCode()))
			.setLicenseUrl(apply.getLicenseUrl())
			.setProvince(apply.getProvince())
			.setCity(apply.getCity())
			.setDistrict(apply.getDistrict())
			.setAddress(apply.getAddress())
			.setContactName(apply.getContactName())
			.setContactPhone(apply.getContactPhone())
			.setAuditStatus(BuyerOrgConstants.AUDIT_APPROVED)
			.setAuditBy(operator)
			.setAuditTime(now)
			.setOwnerUserId(apply.getUserId())
			.setStatus(BuyerOrgConstants.STATUS_NORMAL)
			.setBindSource(BuyerOrgConstants.BIND_SOURCE_APPLY_AUDIT)
			.setTenantId(tenantId);
		buyerOrgMapper.insert(org);
		return org;
	}

	private BuyerOrgVO toOrgVO(BuyerOrg org, BuyerOrgMember member, long memberCount) {
		BuyerOrgVO vo = new BuyerOrgVO();
		vo.setId(org.getId());
		vo.setOrgCode(org.getOrgCode());
		vo.setOrgName(org.getOrgName());
		vo.setOrgType(org.getOrgType());
		vo.setCreditCodeMasked(maskCreditCode(org.getCreditCode()));
		vo.setProvince(org.getProvince());
		vo.setCity(org.getCity());
		vo.setDistrict(org.getDistrict());
		vo.setAddress(org.getAddress());
		vo.setContactName(org.getContactName());
		vo.setContactPhoneMasked(maskPhone(org.getContactPhone()));
		vo.setAuditStatus(org.getAuditStatus());
		vo.setAuditRemark(org.getAuditRemark());
		vo.setOwnerUserId(org.getOwnerUserId());
		vo.setStatus(org.getStatus());
		vo.setBindSource(org.getBindSource());
		vo.setCreateTime(org.getCreateTime());
		vo.setMemberCount((int) memberCount);
		boolean activeMember = member != null
			&& BuyerOrgConstants.MEMBER_STATUS_NORMAL.equals(member.getStatus());
		vo.setJoined(activeMember);
		if (activeMember) {
			vo.setViewerRole(member.getMemberRole());
			vo.setViewerCanEdit("1".equals(member.getCanEdit()));
			vo.setViewerCanConfirm("1".equals(member.getCanConfirm()));
			vo.setViewerIsOwner(userIsOwner(org, member, member.getUserId()));
		}
		return vo;
	}

	private BuyerOrgApplyVO toApplyVO(BuyerOrgApply apply, UserInfoVO user) {
		BuyerOrgApplyVO vo = new BuyerOrgApplyVO();
		vo.setId(apply.getId());
		vo.setApplyNo(apply.getApplyNo());
		vo.setUserId(apply.getUserId());
		if (user != null) {
			vo.setUserName(user.getNickname());
			vo.setUserPhoneMasked(maskPhone(user.getPhone()));
		}
		vo.setApplyType(apply.getApplyType());
		vo.setBuyerOrgId(apply.getBuyerOrgId());
		vo.setOrgName(apply.getOrgName());
		vo.setOrgType(apply.getOrgType());
		vo.setCreditCodeMasked(maskCreditCode(apply.getCreditCode()));
		vo.setProvince(apply.getProvince());
		vo.setCity(apply.getCity());
		vo.setDistrict(apply.getDistrict());
		vo.setAddress(apply.getAddress());
		vo.setContactName(apply.getContactName());
		vo.setContactPhoneMasked(maskPhone(apply.getContactPhone()));
		vo.setApplyRole(apply.getApplyRole());
		vo.setStatus(apply.getStatus());
		vo.setAuditRemark(apply.getAuditRemark());
		vo.setMatchedOrgId(apply.getMatchedOrgId());
		vo.setCreateTime(apply.getCreateTime());
		return vo;
	}

	private BuyerOrgMemberVO toMemberVO(BuyerOrgMember member, UserInfoVO user) {
		BuyerOrgMemberVO vo = new BuyerOrgMemberVO();
		vo.setId(member.getId());
		vo.setBuyerOrgId(member.getBuyerOrgId());
		vo.setUserId(member.getUserId());
		if (user != null) {
			vo.setNickname(user.getNickname());
			vo.setAvatarUrl(user.getAvatarUrl());
			vo.setPhoneMasked(maskPhone(user.getPhone()));
		}
		vo.setMemberRole(member.getMemberRole());
		vo.setCanEdit(member.getCanEdit());
		vo.setCanConfirm(member.getCanConfirm());
		vo.setStatus(member.getStatus());
		vo.setBindSource(member.getBindSource());
		vo.setJoinTime(member.getJoinTime());
		vo.setLeaveTime(member.getLeaveTime());
		return vo;
	}

	private Map<String, UserInfoVO> loadUsers(List<String> userIds) {
		if (userIds == null || userIds.isEmpty()) {
			return Collections.emptyMap();
		}
		try {
			List<UserInfoVO> users = remoteMallUserService.getUserByIds(userIds);
			if (users == null || users.isEmpty()) {
				return Collections.emptyMap();
			}
			Map<String, UserInfoVO> map = new HashMap<>(users.size());
			users.forEach(user -> {
				if (user != null && user.getId() != null) {
					map.put(user.getId(), user);
				}
			});
			return map;
		} catch (Exception ex) {
			log.warn("采购主体成员用户信息补全失败，降级为仅展示用户ID：{}", ex.getMessage());
			return Collections.emptyMap();
		}
	}

	private void validateUserInTenant(String userId, String tenantId) {
		if (!hasText(userId)) {
			throw new ArynBusinessException("请选择用户");
		}
		try {
			UserInfoVO user = remoteMallUserService.getUserById(userId);
			if (user == null) {
				throw new ArynBusinessException("用户不存在");
			}
			if (hasText(user.getTenantId()) && !Objects.equals(user.getTenantId(), tenantId)) {
				throw new ArynBusinessException("只能添加本租户用户");
			}
		} catch (ArynBusinessException ex) {
			throw ex;
		} catch (Exception ex) {
			log.warn("校验采购主体成员用户失败，按用户ID继续：{}", ex.getMessage());
		}
	}

	private String generateUniqueCode(String tenantId) {
		for (int i = 0; i < 20; i++) {
			StringBuilder builder = new StringBuilder(8);
			for (int j = 0; j < 8; j++) {
				builder.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
			}
			String code = builder.toString();
			long count = buyerOrgInviteCodeMapper.selectCount(Wrappers.lambdaQuery(BuyerOrgInviteCode.class)
				.eq(BuyerOrgInviteCode::getTenantId, tenantId)
				.eq(BuyerOrgInviteCode::getCode, code));
			if (count == 0) {
				return code;
			}
		}
		throw new ArynBusinessException("邀请码生成失败，请重试");
	}

	private String normalizeOrgType(String orgType) {
		if (BuyerOrgConstants.ORG_TYPE_ENTERPRISE.equals(orgType)
			|| BuyerOrgConstants.ORG_TYPE_FACTORY.equals(orgType)
			|| BuyerOrgConstants.ORG_TYPE_RESTAURANT.equals(orgType)) {
			return orgType;
		}
		return BuyerOrgConstants.ORG_TYPE_FACTORY;
	}

	private String normalizeRole(String role) {
		if (BuyerOrgConstants.ROLE_OWNER.equals(role)
			|| BuyerOrgConstants.ROLE_BUYER.equals(role)
			|| BuyerOrgConstants.ROLE_SALES.equals(role)
			|| BuyerOrgConstants.ROLE_VIEWER.equals(role)) {
			return role;
		}
		return BuyerOrgConstants.ROLE_BUYER;
	}

	private String defaultFlag(String value, String defaultValue) {
		return "0".equals(value) || "1".equals(value) ? value : defaultValue;
	}

	private String maskCreditCode(String code) {
		if (!hasText(code)) {
			return null;
		}
		String trimmed = code.trim();
		if (trimmed.length() <= 8) {
			return trimmed.substring(0, 2) + "****";
		}
		return trimmed.substring(0, 4) + "****" + trimmed.substring(trimmed.length() - 4);
	}

	private String maskPhone(String phone) {
		if (!hasText(phone)) {
			return null;
		}
		String trimmed = phone.trim();
		if (trimmed.length() >= 7) {
			return trimmed.substring(0, 3) + "****" + trimmed.substring(trimmed.length() - 4);
		}
		return trimmed.substring(0, 1) + "****";
	}

	private String trimToNull(String value) {
		if (value == null) {
			return null;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? null : trimmed;
	}

	private boolean hasText(String value) {
		return value != null && !value.trim().isEmpty();
	}

}
