package com.aryn.cloud.vessel.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.vessel.api.dto.VesselBindApplyDTO;
import com.aryn.cloud.vessel.api.dto.VesselBindAuditDTO;
import com.aryn.cloud.vessel.api.dto.VesselMemberAddDTO;
import com.aryn.cloud.vessel.api.entity.VesselBindApply;
import com.aryn.cloud.vessel.api.entity.VesselInfo;
import com.aryn.cloud.vessel.api.entity.VesselInviteCode;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.aryn.cloud.vessel.api.vo.VesselBindApplyVO;
import com.aryn.cloud.vessel.api.vo.VesselMemberVO;
import com.aryn.cloud.vessel.mapper.VesselBindApplyMapper;
import com.aryn.cloud.vessel.mapper.VesselInfoMapper;
import com.aryn.cloud.vessel.mapper.VesselInviteCodeMapper;
import com.aryn.cloud.vessel.mapper.VesselMemberMapper;
import com.aryn.cloud.vessel.service.IVesselBindService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 船舶自助绑定服务实现。
 *
 * @author aryn
 * @since 2026/9/19
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VesselBindServiceImpl implements IVesselBindService {

	private static final String VESSEL_STATUS_ACTIVE = "1";

	private static final String FLAG_YES = "1";

	private static final String FLAG_NO = "0";

	/** 邀请码字符集：去掉 0/O/1/I 等易混字符，便于船员口头/手抄传递 */
	private static final char[] CODE_CHARS = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

	private static final int CODE_LENGTH = 6;

	/** 默认邀请码有效期（小时） */
	private static final int DEFAULT_EXPIRE_HOURS = 24;

	private static final SecureRandom RANDOM = new SecureRandom();

	/** 可添加 / 移除成员的角色：发起人 / 采购确认人 / 业务员 */
	private static final Set<String> MEMBER_ADMIN_ROLES = Set.of(VesselMember.ROLE_OWNER,
			VesselMember.ROLE_CONFIRMER, VesselMember.ROLE_SALES);

	private final VesselMemberMapper vesselMemberMapper;

	private final VesselBindApplyMapper vesselBindApplyMapper;

	private final VesselInviteCodeMapper vesselInviteCodeMapper;

	private final VesselInfoMapper vesselInfoMapper;

	@DubboReference
	private RemoteMallUserService remoteMallUserService;

	// =====================================================================
	// 申请
	// =====================================================================

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VesselBindApplyVO submitApply(String tenantId, String userId, VesselBindApplyDTO dto) {
		String role = StringUtils.hasText(dto.getApplyRole()) ? dto.getApplyRole() : VesselBindApply.ROLE_CREW;
		if (!VesselBindApply.ROLE_CREW.equals(role) && !VesselBindApply.ROLE_SALES.equals(role)) {
			throw new ArynBusinessException("申请角色不合法");
		}
		// 已是某船成员时不再允许提交：业务员/船员都已在船上，没有申请的必要
		Long existingMembership = vesselMemberMapper.selectCount(Wrappers.lambdaQuery(VesselMember.class)
			.eq(VesselMember::getTenantId, tenantId)
			.eq(VesselMember::getUserId, userId)
			.eq(VesselMember::getStatus, VesselMember.STATUS_ONBOARD));
		if (existingMembership != null && existingMembership > 0) {
			throw new ArynBusinessException("您已绑定船舶，无需重复申请");
		}
		Long pending = vesselBindApplyMapper.selectCount(Wrappers.lambdaQuery(VesselBindApply.class)
			.eq(VesselBindApply::getTenantId, tenantId)
			.eq(VesselBindApply::getUserId, userId)
			.eq(VesselBindApply::getStatus, VesselBindApply.STATUS_PENDING));
		if (pending != null && pending > 0) {
			throw new ArynBusinessException("您有一条待审核的申请，请等待运营处理");
		}

		VesselBindApply apply = new VesselBindApply();
		apply.setApplyNo("VB" + IdWorker.getIdStr());
		apply.setUserId(userId);
		apply.setApplyRole(role);
		apply.setApplyVesselName(dto.getApplyVesselName().trim());
		apply.setApplyVesselImo(dto.getApplyVesselImo());
		apply.setApplyPortName(dto.getApplyPortName());
		apply.setRealName(dto.getRealName());
		apply.setPhone(dto.getPhone());
		apply.setPosition(dto.getPosition());
		apply.setRemark(dto.getRemark());
		apply.setStatus(VesselBindApply.STATUS_PENDING);
		apply.setTenantId(tenantId);
		vesselBindApplyMapper.insert(apply);
		log.info("船舶绑定申请已提交 tenant={} user={} role={} vessel={}", tenantId, userId, role,
				apply.getApplyVesselName());
		return toApplyVO(apply, Map.of());
	}

	@Override
	public List<VesselBindApplyVO> myApplies(String tenantId, String userId) {
		List<VesselBindApply> applies = vesselBindApplyMapper.selectList(Wrappers.lambdaQuery(VesselBindApply.class)
			.eq(VesselBindApply::getTenantId, tenantId)
			.eq(VesselBindApply::getUserId, userId)
			.orderByDesc(VesselBindApply::getCreateTime));
		if (applies.isEmpty()) {
			return List.of();
		}
		return toApplyVOs(applies);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void cancelApply(String tenantId, String userId, String applyId) {
		VesselBindApply apply = requireApply(tenantId, applyId);
		if (!Objects.equals(apply.getUserId(), userId)) {
			throw new ArynBusinessException("只能撤回自己的申请");
		}
		if (!VesselBindApply.STATUS_PENDING.equals(apply.getStatus())) {
			throw new ArynBusinessException("只有待审核的申请可以撤回");
		}
		apply.setStatus(VesselBindApply.STATUS_CANCELED);
		vesselBindApplyMapper.updateById(apply);
	}

	// =====================================================================
	// 成员管理
	// =====================================================================

	@Override
	public List<VesselMemberVO> listMembers(String tenantId, String userId, String vesselId) {
		requireMembership(tenantId, userId, vesselId);
		List<VesselMember> members = vesselMemberMapper.selectList(Wrappers.lambdaQuery(VesselMember.class)
			.eq(VesselMember::getTenantId, tenantId)
			.eq(VesselMember::getVesselId, vesselId)
			.orderByAsc(VesselMember::getJoinTime));
		if (members.isEmpty()) {
			return List.of();
		}
		Map<String, UserInfoVO> users = loadUsers(members.stream().map(VesselMember::getUserId).toList());
		return members.stream().map(member -> toMemberVO(member, users.get(member.getUserId()))).toList();
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VesselMemberVO addMember(String tenantId, String operatorUserId, String vesselId, VesselMemberAddDTO dto) {
		requireMemberAdmin(tenantId, operatorUserId, vesselId);
		requireVesselActive(tenantId, vesselId);

		String targetUserId = resolveTargetUserId(dto);
		if (Objects.equals(targetUserId, operatorUserId)) {
			throw new ArynBusinessException("您已经是该船成员");
		}
		Long duplicated = vesselMemberMapper.selectCount(Wrappers.lambdaQuery(VesselMember.class)
			.eq(VesselMember::getTenantId, tenantId)
			.eq(VesselMember::getVesselId, vesselId)
			.eq(VesselMember::getUserId, targetUserId));
		if (duplicated != null && duplicated > 0) {
			throw new ArynBusinessException("该用户已在船舶成员中");
		}

		String role = StringUtils.hasText(dto.getMemberRole()) ? dto.getMemberRole() : VesselMember.ROLE_CREW;
		VesselMember member = new VesselMember();
		member.setVesselId(vesselId);
		member.setUserId(targetUserId);
		member.setMemberRole(role);
		member.setCanEdit(FLAG_YES);
		member.setCanConfirm(VesselMember.ROLE_CONFIRMER.equals(role) ? FLAG_YES : FLAG_NO);
		member.setStatus(VesselMember.STATUS_ONBOARD);
		member.setJoinTime(LocalDateTime.now());
		member.setRemark(dto.getRemark());
		member.setTenantId(tenantId);
		vesselMemberMapper.insert(member);
		log.info("添加船舶成员 tenant={} vessel={} target={} role={} operator={}", tenantId, vesselId, targetUserId, role,
				operatorUserId);
		return toMemberVO(member, loadUsers(List.of(targetUserId)).get(targetUserId));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void removeMember(String tenantId, String operatorUserId, String vesselId, String memberId) {
		requireMemberAdmin(tenantId, operatorUserId, vesselId);
		VesselMember member = vesselMemberMapper.selectById(memberId);
		if (member == null || !Objects.equals(member.getVesselId(), vesselId)) {
			throw new ArynBusinessException("成员不存在");
		}
		if (Objects.equals(member.getUserId(), operatorUserId)) {
			throw new ArynBusinessException("不能移除自己，请联系其他管理员");
		}
		if (VesselMember.ROLE_OWNER.equals(member.getMemberRole())) {
			throw new ArynBusinessException("不能移除发起人");
		}
		vesselMemberMapper.deleteById(memberId);
	}

	@Override
	public List<VesselMemberVO> searchCandidates(String tenantId, String userId, String vesselId, String keyword) {
		requireMemberAdmin(tenantId, userId, vesselId);
		if (!StringUtils.hasText(keyword)) {
			return List.of();
		}
		List<UserInfoVO> users = remoteMallUserService.searchUsersForBinding(keyword.trim(), 20);
		if (users == null || users.isEmpty()) {
			return List.of();
		}
		Set<String> existing = new HashSet<>(vesselMemberMapper
			.selectList(Wrappers.lambdaQuery(VesselMember.class)
				.eq(VesselMember::getTenantId, tenantId)
				.eq(VesselMember::getVesselId, vesselId))
			.stream()
			.map(VesselMember::getUserId)
			.toList());
		List<VesselMemberVO> result = new ArrayList<>(users.size());
		for (UserInfoVO candidate : users) {
			VesselMemberVO vo = new VesselMemberVO();
			vo.setUserId(candidate.getId());
			vo.setNickname(candidate.getNickname());
			vo.setPhone(maskPhone(candidate.getPhone()));
			// 复用 status 字段承载「是否已在船上」，前端据此禁用「添加」按钮
			vo.setStatus(existing.contains(candidate.getId()) ? FLAG_YES : FLAG_NO);
			result.add(vo);
		}
		return result;
	}

	// =====================================================================
	// 邀请码
	// =====================================================================

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VesselInviteCode generateInviteCode(String tenantId, String userId, String vesselId, Integer maxUses,
			Integer expireHours, String remark) {
		requireMemberAdmin(tenantId, userId, vesselId);
		requireVesselActive(tenantId, vesselId);
		// 同船同人只保留一个有效码，避免多个码并存难以管理
		vesselInviteCodeMapper.delete(Wrappers.lambdaQuery(VesselInviteCode.class)
			.eq(VesselInviteCode::getTenantId, tenantId)
			.eq(VesselInviteCode::getVesselId, vesselId)
			.eq(VesselInviteCode::getOwnerUserId, userId)
			.eq(VesselInviteCode::getStatus, VesselInviteCode.STATUS_ACTIVE));

		VesselInviteCode invite = new VesselInviteCode();
		invite.setVesselId(vesselId);
		invite.setCode(nextUniqueCode(tenantId));
		invite.setOwnerUserId(userId);
		invite.setMaxUses(maxUses == null || maxUses < 0 ? 0 : maxUses);
		invite.setUsedCount(0);
		int hours = expireHours == null || expireHours <= 0 ? DEFAULT_EXPIRE_HOURS : Math.min(expireHours, 24 * 30);
		invite.setExpiresAt(LocalDateTime.now().plusHours(hours));
		invite.setStatus(VesselInviteCode.STATUS_ACTIVE);
		invite.setRemark(remark);
		invite.setTenantId(tenantId);
		vesselInviteCodeMapper.insert(invite);
		return invite;
	}

	@Override
	public List<VesselInviteCode> myInviteCodes(String tenantId, String userId, String vesselId) {
		requireMembership(tenantId, userId, vesselId);
		return vesselInviteCodeMapper.selectList(Wrappers.lambdaQuery(VesselInviteCode.class)
			.eq(VesselInviteCode::getTenantId, tenantId)
			.eq(VesselInviteCode::getVesselId, vesselId)
			.eq(VesselInviteCode::getOwnerUserId, userId)
			.eq(VesselInviteCode::getStatus, VesselInviteCode.STATUS_ACTIVE)
			.gt(VesselInviteCode::getExpiresAt, LocalDateTime.now())
			.orderByDesc(VesselInviteCode::getCreateTime));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void revokeInviteCode(String tenantId, String userId, String codeId) {
		VesselInviteCode invite = vesselInviteCodeMapper.selectById(codeId);
		if (invite == null || !Objects.equals(invite.getTenantId(), tenantId)) {
			throw new ArynBusinessException("邀请码不存在");
		}
		if (!Objects.equals(invite.getOwnerUserId(), userId)) {
			throw new ArynBusinessException("只能撤销自己生成的邀请码");
		}
		invite.setStatus(VesselInviteCode.STATUS_REVOKED);
		vesselInviteCodeMapper.updateById(invite);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VesselMemberVO redeemInviteCode(String tenantId, String userId, String code) {
		if (!StringUtils.hasText(code)) {
			throw new ArynBusinessException("请输入邀请码");
		}
		VesselInviteCode invite = vesselInviteCodeMapper.selectOne(Wrappers.lambdaQuery(VesselInviteCode.class)
			.eq(VesselInviteCode::getTenantId, tenantId)
			.eq(VesselInviteCode::getCode, code.trim().toUpperCase()));
		if (invite == null) {
			throw new ArynBusinessException("邀请码不存在");
		}
		if (!VesselInviteCode.STATUS_ACTIVE.equals(invite.getStatus())) {
			throw new ArynBusinessException("邀请码已失效");
		}
		if (invite.getExpiresAt() != null && invite.getExpiresAt().isBefore(LocalDateTime.now())) {
			throw new ArynBusinessException("邀请码已过期");
		}
		if (invite.getMaxUses() != null && invite.getMaxUses() > 0
				&& invite.getUsedCount() != null && invite.getUsedCount() >= invite.getMaxUses()) {
			throw new ArynBusinessException("邀请码使用次数已达上限");
		}
		Long duplicated = vesselMemberMapper.selectCount(Wrappers.lambdaQuery(VesselMember.class)
			.eq(VesselMember::getTenantId, tenantId)
			.eq(VesselMember::getVesselId, invite.getVesselId())
			.eq(VesselMember::getUserId, userId));
		if (duplicated != null && duplicated > 0) {
			throw new ArynBusinessException("您已是该船成员");
		}
		requireVesselActive(tenantId, invite.getVesselId());

		VesselMember member = new VesselMember();
		member.setVesselId(invite.getVesselId());
		member.setUserId(userId);
		member.setMemberRole(VesselMember.ROLE_CREW);
		member.setCanEdit(FLAG_YES);
		member.setCanConfirm(FLAG_NO);
		member.setStatus(VesselMember.STATUS_ONBOARD);
		member.setJoinTime(LocalDateTime.now());
		member.setRemark("通过邀请码加入");
		member.setTenantId(tenantId);
		vesselMemberMapper.insert(member);

		invite.setUsedCount((invite.getUsedCount() == null ? 0 : invite.getUsedCount()) + 1);
		vesselInviteCodeMapper.updateById(invite);
		log.info("邀请码兑换成功 tenant={} code={} user={} vessel={}", tenantId, invite.getCode(), userId,
				invite.getVesselId());
		return toMemberVO(member, loadUsers(List.of(userId)).get(userId));
	}

	// =====================================================================
	// 管理端审核
	// =====================================================================

	@Override
	public IPage<VesselBindApplyVO> adminPage(Page<VesselBindApplyVO> page, String tenantId, String status,
			String vesselName) {
		IPage<VesselBindApply> result = vesselBindApplyMapper.selectPage(
			new Page<>(page.getCurrent(), page.getSize()),
			Wrappers.lambdaQuery(VesselBindApply.class)
				.eq(VesselBindApply::getTenantId, tenantId)
				.eq(StringUtils.hasText(status), VesselBindApply::getStatus, status)
				.like(StringUtils.hasText(vesselName), VesselBindApply::getApplyVesselName, vesselName)
				.orderByAsc(VesselBindApply::getStatus)
				.orderByDesc(VesselBindApply::getCreateTime));
		Page<VesselBindApplyVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
		voPage.setRecords(toApplyVOs(result.getRecords()));
		return voPage;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String approveApply(String tenantId, String adminUserId, String applyId, VesselBindAuditDTO dto) {
		VesselBindApply apply = requireApply(tenantId, applyId);
		if (!VesselBindApply.STATUS_PENDING.equals(apply.getStatus())) {
			throw new ArynBusinessException("该申请已处理，不能重复审核");
		}
		String vesselId = resolveVesselForApprove(tenantId, apply, dto);
		String role = dto != null && StringUtils.hasText(dto.getMemberRole()) ? dto.getMemberRole()
				: apply.getApplyRole();

		Long duplicated = vesselMemberMapper.selectCount(Wrappers.lambdaQuery(VesselMember.class)
			.eq(VesselMember::getTenantId, tenantId)
			.eq(VesselMember::getVesselId, vesselId)
			.eq(VesselMember::getUserId, apply.getUserId()));
		if (duplicated == null || duplicated == 0) {
			VesselMember member = new VesselMember();
			member.setVesselId(vesselId);
			member.setUserId(apply.getUserId());
			member.setMemberRole(role);
			member.setCanEdit(FLAG_YES);
			member.setCanConfirm(VesselMember.ROLE_CONFIRMER.equals(role) ? FLAG_YES : FLAG_NO);
			member.setStatus(VesselMember.STATUS_ONBOARD);
			member.setJoinTime(LocalDateTime.now());
			member.setRemark("绑定申请审核通过（申请单 " + apply.getApplyNo() + "）");
			member.setTenantId(tenantId);
			vesselMemberMapper.insert(member);
		}

		apply.setStatus(VesselBindApply.STATUS_APPROVED);
		apply.setMatchedVesselId(vesselId);
		apply.setAuditBy(adminUserId);
		apply.setAuditTime(LocalDateTime.now());
		if (dto != null) {
			apply.setAuditRemark(dto.getAuditRemark());
		}
		vesselBindApplyMapper.updateById(apply);
		log.info("绑定申请审核通过 tenant={} apply={} vessel={} user={} role={}", tenantId, applyId, vesselId,
				apply.getUserId(), role);
		return vesselId;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void rejectApply(String tenantId, String adminUserId, String applyId, String auditRemark) {
		VesselBindApply apply = requireApply(tenantId, applyId);
		if (!VesselBindApply.STATUS_PENDING.equals(apply.getStatus())) {
			throw new ArynBusinessException("该申请已处理，不能重复审核");
		}
		if (!StringUtils.hasText(auditRemark)) {
			throw new ArynBusinessException("驳回必须填写原因");
		}
		apply.setStatus(VesselBindApply.STATUS_REJECTED);
		apply.setAuditBy(adminUserId);
		apply.setAuditTime(LocalDateTime.now());
		apply.setAuditRemark(auditRemark);
		vesselBindApplyMapper.updateById(apply);
	}

	// =====================================================================
	// 内部方法
	// =====================================================================

	/**
	 * 审核通过时确定目标船舶：优先用显式指定的已有船，其次按新船信息创建，
	 * 最后回落到「按申请船名精确匹配」。
	 */
	private String resolveVesselForApprove(String tenantId, VesselBindApply apply, VesselBindAuditDTO dto) {
		if (dto != null && StringUtils.hasText(dto.getMatchedVesselId())) {
			requireVesselActive(tenantId, dto.getMatchedVesselId());
			return dto.getMatchedVesselId();
		}
		String newName = dto != null && StringUtils.hasText(dto.getNewVesselName()) ? dto.getNewVesselName()
				: null;
		if (newName == null) {
			// 未显式指定：按申请船名精确匹配系统内已有船舶
			VesselInfo matched = vesselInfoMapper.selectOne(Wrappers.lambdaQuery(VesselInfo.class)
				.eq(VesselInfo::getTenantId, tenantId)
				.eq(VesselInfo::getVesselName, apply.getApplyVesselName())
				.eq(VesselInfo::getStatus, VESSEL_STATUS_ACTIVE)
				.last("LIMIT 1"));
			if (matched != null) {
				return matched.getId();
			}
			throw new ArynBusinessException("系统内没有名为「" + apply.getApplyVesselName()
					+ "」的在营船舶，请选择已有船舶或填写新船名称后通过");
		}
		// 船还没录入系统 —— 先建船再绑定
		VesselInfo vessel = new VesselInfo();
		vessel.setVesselName(newName.trim());
		vessel.setImoCode(dto.getNewVesselImo());
		vessel.setVesselType(StringUtils.hasText(dto.getNewVesselType()) ? dto.getNewVesselType() : "5");
		vessel.setStatus(VESSEL_STATUS_ACTIVE);
		vessel.setRemark("由绑定申请审核创建（申请单 " + apply.getApplyNo() + "）");
		vessel.setTenantId(tenantId);
		vesselInfoMapper.insert(vessel);
		log.info("审核通过时新建船舶 tenant={} vessel={} name={}", tenantId, vessel.getId(), vessel.getVesselName());
		return vessel.getId();
	}

	private String resolveTargetUserId(VesselMemberAddDTO dto) {
		if (StringUtils.hasText(dto.getUserId())) {
			return dto.getUserId();
		}
		if (!StringUtils.hasText(dto.getPhone())) {
			throw new ArynBusinessException("请提供用户ID或手机号");
		}
		UserInfoVO user = remoteMallUserService.getUserByPhone(dto.getPhone().trim());
		if (user == null || !StringUtils.hasText(user.getId())) {
			throw new ArynBusinessException("未找到该手机号对应的商城用户，请确认对方已注册");
		}
		return user.getId();
	}

	private String nextUniqueCode(String tenantId) {
		for (int attempt = 0; attempt < 20; attempt++) {
			StringBuilder sb = new StringBuilder(CODE_LENGTH);
			for (int i = 0; i < CODE_LENGTH; i++) {
				sb.append(CODE_CHARS[RANDOM.nextInt(CODE_CHARS.length)]);
			}
			String candidate = sb.toString();
			Long exists = vesselInviteCodeMapper.selectCount(Wrappers.lambdaQuery(VesselInviteCode.class)
				.eq(VesselInviteCode::getTenantId, tenantId)
				.eq(VesselInviteCode::getCode, candidate));
			if (exists == null || exists == 0) {
				return candidate;
			}
		}
		throw new ArynBusinessException("邀请码生成失败，请重试");
	}

	private VesselBindApply requireApply(String tenantId, String applyId) {
		VesselBindApply apply = vesselBindApplyMapper.selectOne(Wrappers.lambdaQuery(VesselBindApply.class)
			.eq(VesselBindApply::getTenantId, tenantId)
			.eq(VesselBindApply::getId, applyId));
		if (apply == null) {
			throw new ArynBusinessException("申请不存在");
		}
		return apply;
	}

	private void requireVesselActive(String tenantId, String vesselId) {
		VesselInfo vessel = vesselInfoMapper.selectOne(Wrappers.lambdaQuery(VesselInfo.class)
			.eq(VesselInfo::getTenantId, tenantId)
			.eq(VesselInfo::getId, vesselId)
			.eq(VesselInfo::getStatus, VESSEL_STATUS_ACTIVE));
		if (vessel == null) {
			throw new ArynBusinessException("船舶不存在或已停用");
		}
	}

	private void requireMembership(String tenantId, String userId, String vesselId) {
		Long count = vesselMemberMapper.selectCount(Wrappers.lambdaQuery(VesselMember.class)
			.eq(VesselMember::getTenantId, tenantId)
			.eq(VesselMember::getVesselId, vesselId)
			.eq(VesselMember::getUserId, userId)
			.eq(VesselMember::getStatus, VesselMember.STATUS_ONBOARD));
		if (count == null || count == 0) {
			throw new ArynBusinessException("您不是该船舶成员，无权查看");
		}
	}

	/**
	 * 成员管理权限：发起人 / 采购确认人 / 业务员。
	 * 普通船员不可添加或移除成员——这是「限特定角色」的具体落点。
	 */
	private void requireMemberAdmin(String tenantId, String userId, String vesselId) {
		VesselMember member = vesselMemberMapper.selectOne(Wrappers.lambdaQuery(VesselMember.class)
			.eq(VesselMember::getTenantId, tenantId)
			.eq(VesselMember::getVesselId, vesselId)
			.eq(VesselMember::getUserId, userId)
			.eq(VesselMember::getStatus, VesselMember.STATUS_ONBOARD));
		if (member == null) {
			throw new ArynBusinessException("您不是该船舶成员，无权操作");
		}
		if (!MEMBER_ADMIN_ROLES.contains(member.getMemberRole())) {
			throw new ArynBusinessException("普通船员不能管理成员，请联系船长或业务员");
		}
	}

	private Map<String, UserInfoVO> loadUsers(List<String> userIds) {
		List<String> distinct = userIds.stream().filter(StringUtils::hasText).distinct().toList();
		if (distinct.isEmpty()) {
			return Collections.emptyMap();
		}
		try {
			List<UserInfoVO> users = remoteMallUserService.getUserByIds(distinct);
			Map<String, UserInfoVO> map = new HashMap<>(distinct.size());
			if (users != null) {
				users.forEach(user -> map.put(user.getId(), user));
			}
			return map;
		}
		catch (Exception ex) {
			// 用户域不可用时降级为仅展示 ID，不影响成员管理主流程
			log.warn("回填商城用户信息失败，降级为仅展示用户ID，userIds={}", distinct, ex);
			return Collections.emptyMap();
		}
	}

	private List<VesselBindApplyVO> toApplyVOs(List<VesselBindApply> applies) {
		if (applies == null || applies.isEmpty()) {
			return List.of();
		}
		Map<String, UserInfoVO> users = loadUsers(applies.stream().map(VesselBindApply::getUserId).toList());
		Map<String, String> vesselNames = loadVesselNames(applies.stream()
			.map(VesselBindApply::getMatchedVesselId)
			.filter(StringUtils::hasText)
			.distinct()
			.toList());
		return applies.stream()
			.map(apply -> {
				VesselBindApplyVO vo = toApplyVO(apply, users);
				if (StringUtils.hasText(apply.getMatchedVesselId())) {
					vo.setMatchedVesselName(vesselNames.get(apply.getMatchedVesselId()));
				}
				return vo;
			})
			.toList();
	}

	private VesselBindApplyVO toApplyVO(VesselBindApply apply, Map<String, UserInfoVO> users) {
		VesselBindApplyVO vo = new VesselBindApplyVO();
		vo.setId(apply.getId());
		vo.setApplyNo(apply.getApplyNo());
		vo.setUserId(apply.getUserId());
		UserInfoVO user = users.get(apply.getUserId());
		if (user != null) {
			vo.setUserNickname(user.getNickname());
			vo.setUserPhone(maskPhone(user.getPhone()));
		}
		vo.setApplyRole(apply.getApplyRole());
		vo.setApplyVesselName(apply.getApplyVesselName());
		vo.setApplyVesselImo(apply.getApplyVesselImo());
		vo.setApplyPortName(apply.getApplyPortName());
		vo.setRealName(apply.getRealName());
		vo.setPhone(apply.getPhone());
		vo.setPosition(apply.getPosition());
		vo.setRemark(apply.getRemark());
		vo.setStatus(apply.getStatus());
		vo.setMatchedVesselId(apply.getMatchedVesselId());
		vo.setAuditRemark(apply.getAuditRemark());
		vo.setAuditTime(apply.getAuditTime());
		vo.setCreateTime(apply.getCreateTime());
		return vo;
	}

	private Map<String, String> loadVesselNames(List<String> vesselIds) {
		if (vesselIds.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, String> map = new HashMap<>(vesselIds.size());
		vesselInfoMapper.selectList(Wrappers.lambdaQuery(VesselInfo.class).in(VesselInfo::getId, vesselIds))
			.forEach(vessel -> map.put(vessel.getId(), vessel.getVesselName()));
		return map;
	}

	private VesselMemberVO toMemberVO(VesselMember member, UserInfoVO user) {
		VesselMemberVO vo = new VesselMemberVO();
		vo.setId(member.getId());
		vo.setUserId(member.getUserId());
		vo.setMemberRole(member.getMemberRole());
		vo.setStatus(member.getStatus());
		vo.setRemark(member.getRemark());
		vo.setJoinTime(member.getJoinTime());
		if (user != null) {
			vo.setNickname(user.getNickname());
			vo.setPhone(maskPhone(user.getPhone()));
		}
		return vo;
	}

	/** 手机号脱敏：保留前三后四 */
	private String maskPhone(String phone) {
		if (!StringUtils.hasText(phone) || phone.length() < 7) {
			return phone;
		}
		return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
	}

}
