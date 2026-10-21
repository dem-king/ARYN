package com.aryn.cloud.vessel.service.impl;

import com.aryn.cloud.common.core.constant.CommonConstants;
import com.aryn.cloud.common.core.constant.RocketMqConstants;
import com.aryn.cloud.common.security.entity.ArynUser;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.common.security.util.SecurityUtils;
import com.aryn.cloud.message.api.constant.MessageJumpConstants;
import com.aryn.cloud.message.api.dto.MessageSendCommand;
import com.aryn.cloud.message.api.enums.MessageIdentityType;
import com.aryn.cloud.pay.api.utils.TransactionalMqUtils;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import com.aryn.cloud.user.api.remote.RemoteMallUserService;
import com.aryn.cloud.user.api.vo.UserInfoVO;
import com.aryn.cloud.vessel.api.constant.VesselMemberBindSource;
import com.aryn.cloud.vessel.api.constant.VesselMemberRole;
import com.aryn.cloud.vessel.api.constant.VesselType;
import com.aryn.cloud.vessel.api.dto.VesselBindApplyDTO;
import com.aryn.cloud.vessel.api.dto.VesselBindAuditDTO;
import com.aryn.cloud.vessel.api.dto.VesselMemberAddDTO;
import com.aryn.cloud.vessel.api.entity.VesselBindApply;
import com.aryn.cloud.vessel.api.entity.VesselInfo;
import com.aryn.cloud.vessel.api.entity.VesselInviteCode;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.aryn.cloud.vessel.api.entity.VesselMemberAuditLog;
import com.aryn.cloud.vessel.api.vo.VesselBindApplyVO;
import com.aryn.cloud.vessel.api.vo.VesselBindPrecheckVO;
import com.aryn.cloud.vessel.api.vo.VesselBindVesselOptionVO;
import com.aryn.cloud.vessel.api.vo.VesselMemberVO;
import com.aryn.cloud.vessel.mapper.VesselBindApplyMapper;
import com.aryn.cloud.vessel.mapper.VesselInfoMapper;
import com.aryn.cloud.vessel.mapper.VesselInviteCodeMapper;
import com.aryn.cloud.vessel.mapper.VesselMemberAuditLogMapper;
import com.aryn.cloud.vessel.mapper.VesselMemberMapper;
import com.aryn.cloud.vessel.service.IVesselBindService;
import com.aryn.cloud.vessel.support.VesselMemberRegistrar;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
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

	/** 站内信类目 / 业务类型（审核结果） */
	private static final String CATEGORY_BIND_AUDIT = "VESSEL_BIND_AUDIT";

	private static final String BIZ_TYPE_BIND_APPLY = "VESSEL_BIND_APPLY";

	/** 站内信类目 / 业务类型（有待审核的新申请，发给运营） */
	private static final String CATEGORY_BIND_APPLY_TODO = "VESSEL_BIND_TODO";

	private static final String BIZ_TYPE_BIND_APPLY_TODO = "VESSEL_BIND_APPLY_TODO";

	/** C 端船舶绑定页：申请人看审核结果后可直接去核对/重新提交 */
	private static final String BIND_PAGE_ROUTE = "/sub-pages/vessel/bind/index";

	/** 管理端绑定申请列表：运营收到「有新申请」后去审核 */
	private static final String BIND_APPLY_ADMIN_ROUTE = "/vessel/bind-apply";

	/**
	 * 审核权限码。
	 *
	 * <p>收件人按「谁有权审核」筛而不是按角色码筛：本页菜单授权是从「船供运营」
	 * 菜单推导的（见 64vessel_bind_admin_menu.sql），设计上允许运营角色持有，
	 * 只发 ROLE_ADMIN 会在角色拆分后静默漏通知。
	 */
	private static final String PERMISSION_BIND_AUDIT = "vessel:bindapply:audit";

	private final VesselMemberMapper vesselMemberMapper;

	private final VesselBindApplyMapper vesselBindApplyMapper;

	private final VesselInviteCodeMapper vesselInviteCodeMapper;

	private final VesselInfoMapper vesselInfoMapper;

	/** 成员关系落库唯一入口：新建与复活收敛在一处，避免散落的 insert 撞唯一键 */
	private final VesselMemberRegistrar memberRegistrar;

	/** 成员关系审计留痕（追加写）：解绑这类带明确操作人的动作由本服务直接写 */
	private final VesselMemberAuditLogMapper vesselMemberAuditLogMapper;

	private final RocketMQTemplate rocketMQTemplate;

	private final ObjectMapper objectMapper;

	@DubboReference
	private RemoteMallUserService remoteMallUserService;

	/**
	 * 消息域工作人员受众查询（按审核权限筛运营收件人）。
	 *
	 * <p>字段注入而非构造器注入：boot 模式 injvm 下构造器按类型注入远程接口
	 * 容易与本地实现 bean 产生二义，既有代码（CodPayRemindServiceImpl）同此约定。
	 */
	@DubboReference
	private RemoteMessageStaffService remoteMessageStaffService;

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
		// 一人多船模型下，已在船的用户同样可能申请加入其他船或认领新船；
		// 是否放行由运营审核把关，这里只拦同一用户重复的待审核申请
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
		apply.setApplyVesselImo(trimToNull(dto.getApplyVesselImo()));
		apply.setApplyVesselCallSign(trimToNull(dto.getApplyVesselCallSign()));
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
		// 提交后通知有审核权限的运营：此前只有申请结果会通知申请人，
		// 新申请上来时运营不刷新管理端列表就完全不知道（铃铛/语音播报同理）
		TransactionalMqUtils.sendAfterCommit(() -> notifyReviewers(tenantId, apply));
		return toApplyVO(apply, Map.of());
	}

	/**
	 * 新绑定申请通知有审核权限的工作人员（站内信 + 管理端铃铛/语音/桌面提醒）。
	 *
	 * <p>必须在事务提交后调用（{@link TransactionalMqUtils#sendAfterCommit}）：
	 * 回滚时不留「申请没落库却通知了运营」的误报。发送失败只告警，不影响申请本身
	 * （幂等由站内信来源唯一键兜底，eventId 带申请单主键）。
	 */
	private void notifyReviewers(String tenantId, VesselBindApply apply) {
		List<StaffMessageRecipientVO> reviewers = queryReviewers(tenantId, apply);
		if (CollectionUtils.isEmpty(reviewers)) {
			log.warn("绑定申请无可用收件人，跳过通知 tenant={} apply={}", tenantId, apply.getApplyNo());
			return;
		}
		boolean sales = VesselBindApply.ROLE_SALES.equals(apply.getApplyRole());
		String roleText = sales ? "商城业务员" : "普通船员";
		String title = sales ? "有新的业务员认领船舶申请" : "有新的船员绑定申请";
		String vesselName = displayOr(apply.getApplyVesselName(), "未填写船名");
		String applicant = displayOr(apply.getRealName(), "未填写姓名");
		String summary = roleText + "「" + applicant + "」申请绑定船舶「" + vesselName + "」，待审核";
		StringBuilder content = new StringBuilder();
		content.append(roleText).append("「").append(applicant).append("」提交了船舶绑定申请（申请单 ")
			.append(apply.getApplyNo()).append("）。船名：").append(vesselName).append("。");
		content.append("联系电话：").append(displayOr(apply.getPhone(), "未填写")).append("。");
		if (StringUtils.hasText(apply.getApplyVesselImo())) {
			content.append("IMO：").append(apply.getApplyVesselImo()).append("。");
		}
		if (StringUtils.hasText(apply.getApplyVesselCallSign())) {
			content.append("呼号：").append(apply.getApplyVesselCallSign()).append("。");
		}
		if (StringUtils.hasText(apply.getApplyPortName())) {
			content.append("常靠港口：").append(apply.getApplyPortName()).append("。");
		}
		if (StringUtils.hasText(apply.getPosition())) {
			content.append("船上职务：").append(apply.getPosition()).append("。");
		}
		if (StringUtils.hasText(apply.getRemark())) {
			content.append("补充说明：").append(apply.getRemark()).append("。");
		}
		content.append("请到「船供运营 - 绑定申请」审核。");

		for (StaffMessageRecipientVO reviewer : reviewers) {
			try {
				MessageSendCommand command = new MessageSendCommand();
				command.setEventId("vessel-bind-apply:todo:" + apply.getId());
				command.setTenantId(tenantId);
				command.setRecipientType(MessageIdentityType.SYS_USER.name());
				command.setRecipientId(reviewer.getId());
				command.setRecipientName(reviewer.getNickname());
				command.setCategory(CATEGORY_BIND_APPLY_TODO);
				command.setTitle(title);
				command.setSummary(summary);
				command.setContent(content.toString());
				command.setBizType(BIZ_TYPE_BIND_APPLY_TODO);
				command.setBizId(apply.getId());
				// jumpPayload 落 json 列，必须包成 JSON 对象，裸串会被消费端校验拒绝
				command.setJumpPayload(objectMapper.writeValueAsString(
					Map.of(MessageJumpConstants.ROUTE, BIND_APPLY_ADMIN_ROUTE)));
				command.setChannels(List.of("IN_APP"));
				rocketMQTemplate.convertAndSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC, command);
			}
			catch (Exception ex) {
				log.warn("绑定申请通知发送失败 apply={} reviewer={}: {}", apply.getApplyNo(), reviewer.getId(),
						ex.getMessage());
			}
		}
	}

	/**
	 * 解析新申请的提醒收件人：优先「持有审核权限的人」，无人持权限时回落租户管理员。
	 *
	 * <p>回落的意义：新租户/未执行菜单授权脚本时审核权限尚未授出，若此时静默跳过，
	 * 就又回到「船员提交了申请但运营完全不知道」的原问题。管理员收到后可以补授权或直接处理；
	 * 权限已授出时不会走回落，避免打扰无关管理员。
	 */
	private List<StaffMessageRecipientVO> queryReviewers(String tenantId, VesselBindApply apply) {
		try {
			List<StaffMessageRecipientVO> reviewers = remoteMessageStaffService.queryRecipientsByPermission(tenantId,
					List.of(PERMISSION_BIND_AUDIT));
			if (!CollectionUtils.isEmpty(reviewers)) {
				return reviewers;
			}
			log.warn("绑定申请无审核权限收件人，回落租户管理员 tenant={} apply={}", tenantId, apply.getApplyNo());
			return remoteMessageStaffService.queryRecipientsByRoleCode(tenantId, CommonConstants.ROLE_ADMIN_CODE);
		}
		catch (Exception ex) {
			log.warn("绑定申请审核人查询失败，跳过通知 apply={}: {}", apply.getApplyNo(), ex.getMessage());
			return List.of();
		}
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

		// 角色白名单放在所有查询之前：非法角色不必浪费 SELECT，也保证任何路径都不写库。
		// 此前 memberRole 直接取自请求体，传 "1" 就能把被拉进来的人写成「发起人」，
		// 相当于绕过审核拿到该船最高管理权。Registrar 里还有一层兜底，这里先给场景化报错。
		String role = StringUtils.hasText(dto.getMemberRole()) ? dto.getMemberRole() : VesselMemberRole.CREW;
		if (!VesselMemberRole.isAssignable(role)) {
			throw new ArynBusinessException("成员角色不合法：" + role);
		}

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

		// 走统一入口：被移出过的人重新拉进来会复用历史行（唯一键不含 del_flag）
		VesselMemberRegistrar.OnboardResult result = memberRegistrar.ensureOnboard(tenantId, vesselId, targetUserId, role,
				FLAG_YES, VesselMemberRole.CONFIRMER.equals(role) ? FLAG_YES : FLAG_NO, dto.getRemark(),
				VesselMemberBindSource.MEMBER_ADD);
		log.info("添加船舶成员 tenant={} vessel={} target={} role={} operator={}", tenantId, vesselId, targetUserId, role,
				operatorUserId);
		return toMemberVO(result.member(), loadUsers(List.of(targetUserId)).get(targetUserId));
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
	@Transactional(rollbackFor = Exception.class)
	public void leaveShip(String tenantId, String userId, String vesselId) {
		VesselMember member = vesselMemberMapper.selectOne(Wrappers.lambdaQuery(VesselMember.class)
			.eq(VesselMember::getTenantId, tenantId)
			.eq(VesselMember::getVesselId, vesselId)
			.eq(VesselMember::getUserId, userId)
			.eq(VesselMember::getStatus, VesselMember.STATUS_ONBOARD));
		if (member == null) {
			throw new ArynBusinessException("您不是该船舶成员");
		}
		if (VesselMember.ROLE_OWNER.equals(member.getMemberRole())) {
			// 与 removeMember 的「不能移除发起人」对称：发起人退出后该船将没有管理角色
			throw new ArynBusinessException("发起人暂不支持退出，请联系平台客服处理");
		}
		vesselMemberMapper.deleteById(member.getId());
		// 本人离开后邀请码不能再被他人使用
		vesselInviteCodeMapper.delete(Wrappers.lambdaQuery(VesselInviteCode.class)
			.eq(VesselInviteCode::getTenantId, tenantId)
			.eq(VesselInviteCode::getVesselId, vesselId)
			.eq(VesselInviteCode::getOwnerUserId, userId)
			.eq(VesselInviteCode::getStatus, VesselInviteCode.STATUS_ACTIVE));
		log.info("成员退出船舶 tenant={} vessel={} user={}", tenantId, vesselId, userId);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public void adminUnbindMember(String tenantId, String adminUserId, String vesselId, String userId, String reason) {
		if (!StringUtils.hasText(userId)) {
			throw new ArynBusinessException("请选择要解绑的成员");
		}
		requireVesselActive(tenantId, vesselId);
		VesselMember member = vesselMemberMapper.selectOne(Wrappers.lambdaQuery(VesselMember.class)
			.eq(VesselMember::getTenantId, tenantId)
			.eq(VesselMember::getVesselId, vesselId)
			.eq(VesselMember::getUserId, userId)
			.eq(VesselMember::getStatus, VesselMember.STATUS_ONBOARD));
		if (member == null) {
			throw new ArynBusinessException("该用户不是本船在船成员");
		}
		// 与 C 端退出/移除成员同口径：发起人解绑后该船将没有管理角色
		if (VesselMember.ROLE_OWNER.equals(member.getMemberRole())) {
			throw new ArynBusinessException("不能解绑发起人；如需变更请先在该船指定新的管理角色");
		}

		String remark = StringUtils.hasText(reason) ? "运营解绑：" + reason.trim() : "运营解绑";
		String oldRole = member.getMemberRole();
		vesselMemberMapper.deleteById(member.getId());
		// 解绑后其有效邀请码立即作废，避免本人（或转发出去）继续拉人进船
		vesselInviteCodeMapper.delete(Wrappers.lambdaQuery(VesselInviteCode.class)
			.eq(VesselInviteCode::getTenantId, tenantId)
			.eq(VesselInviteCode::getVesselId, vesselId)
			.eq(VesselInviteCode::getOwnerUserId, userId)
			.eq(VesselInviteCode::getStatus, VesselInviteCode.STATUS_ACTIVE));
		// 解绑此前只有一行 log.info，误绑追查时既查不到「谁解的」也查不到「当时是什么角色」。
		// 解绑是回收数据权限的动作，必须留痕；operator 取当前管理端登录人。
		writeUnbindAudit(tenantId, vesselId, userId, oldRole, member.getBindSource(), remark, adminUserId);
		log.info("运营解绑成员 tenant={} vessel={} user={} operator={} remark={}", tenantId, vesselId, userId,
				adminUserId, remark);
		// 告知本人，避免其加购时才发现「已不在船上」
		TransactionalMqUtils.sendAfterCommit(() -> notifyUnbound(tenantId, vesselId, userId, remark));
	}

	/**
	 * 写解绑审计。
	 *
	 * <p>与 Registrar 的内置审计同构，区别是本动作有明确操作人（当前管理端登录人）——
	 * 「运营回收了谁的船籍」必须能追到人，这是误绑纠错的责任链。
	 *
	 * <p>操作人取自安全上下文；取不到（如内部调用/测试环境未登录）时降级为传入的 adminUserId，
	 * 仍然记下「有过这次解绑」，只是姓名快照为空。审计失败只告警，不阻断解绑。
	 */
	private void writeUnbindAudit(String tenantId, String vesselId, String userId, String oldRole, String bindSource,
			String reason, String adminUserId) {
		try {
			VesselMemberAuditLog audit = new VesselMemberAuditLog();
			audit.setId(IdWorker.getIdStr());
			audit.setTenantId(tenantId);
			audit.setVesselId(vesselId);
			audit.setUserId(userId);
			audit.setAction(VesselMemberAuditLog.ACTION_UNBIND);
			// 审计里的 bind_source 记「被解绑的这条关系当初是怎么来的」，便于按来源统计误绑
			audit.setBindSource(bindSource);
			audit.setOldMemberRole(oldRole);
			audit.setReason(reason);
			audit.setOperatorId(adminUserId);
			audit.setOperatorName(currentOperatorName());
			audit.setCreateTime(LocalDateTime.now());
			audit.setDelFlag("0");
			vesselMemberAuditLogMapper.insert(audit);
		}
		catch (Exception ex) {
			log.warn("成员解绑审计写入失败（不阻断解绑） tenant={} vessel={} user={}: {}", tenantId, vesselId, userId,
					ex.getMessage());
		}
	}

	/** 当前登录人姓名（快照用）；无安全上下文时返回 null，不抛异常影响主流程 */
	private String currentOperatorName() {
		try {
			ArynUser user = SecurityUtils.getUser();
			return user == null ? null : user.getNickname();
		}
		catch (Exception ex) {
			return null;
		}
	}

	/** 解绑通知被移除的用户（站内信）。失败只告警，不影响解绑结果。 */
	private void notifyUnbound(String tenantId, String vesselId, String userId, String remark) {
		try {
			VesselInfo vessel = vesselInfoMapper.selectById(vesselId);
			String vesselName = vessel == null ? vesselId : vessel.getVesselName();
			MessageSendCommand command = new MessageSendCommand();
			// eventId 带成员行主键语义（vessel+user），重复解绑不会重复入箱
			command.setEventId("vessel-unbind:" + vesselId + ":" + userId + ":" + System.currentTimeMillis());
			command.setTenantId(tenantId);
			command.setRecipientType(MessageIdentityType.MALL_USER.name());
			command.setRecipientId(userId);
			command.setCategory(CATEGORY_BIND_AUDIT);
			command.setTitle("已解除船舶绑定");
			command.setSummary("您已被移出「" + vesselName + "」");
			StringBuilder content = new StringBuilder("运营已将您从「").append(vesselName).append("」移出。");
			if (StringUtils.hasText(remark)) {
				content.append("说明：").append(remark).append("。");
			}
			content.append("如为误操作，可联系运营重新绑定。");
			command.setContent(content.toString());
			command.setBizType(BIZ_TYPE_BIND_APPLY);
			command.setBizId(vesselId);
			command.setJumpPayload(objectMapper.writeValueAsString(
				Map.of(MessageJumpConstants.ROUTE, BIND_PAGE_ROUTE)));
			command.setChannels(List.of("IN_APP"));
			rocketMQTemplate.convertAndSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC, command);
		}
		catch (Exception ex) {
			log.warn("解绑通知发送失败 vessel={} user={}: {}", vesselId, userId, ex.getMessage());
		}
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

		// 走统一入口：退出/被解绑过的用户会复用历史行（唯一键不含 del_flag，直接 insert 必撞键）
		VesselMemberRegistrar.OnboardResult result = memberRegistrar.ensureOnboard(tenantId, invite.getVesselId(), userId,
				VesselMemberRole.CREW, FLAG_YES, FLAG_NO, "通过邀请码加入", VesselMemberBindSource.INVITE_CODE);

		invite.setUsedCount((invite.getUsedCount() == null ? 0 : invite.getUsedCount()) + 1);
		vesselInviteCodeMapper.updateById(invite);
		log.info("邀请码兑换成功 tenant={} code={} user={} vessel={}", tenantId, invite.getCode(), userId,
				invite.getVesselId());
		return toMemberVO(result.member(), loadUsers(List.of(userId)).get(userId));
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
	public VesselBindPrecheckVO precheckApply(String tenantId, String applyId) {
		VesselBindApply apply = requireApply(tenantId, applyId);
		String name = trimOrEmpty(apply.getApplyVesselName());
		String imo = trimOrEmpty(apply.getApplyVesselImo());
		String callSign = trimOrEmpty(apply.getApplyVesselCallSign());

		VesselBindPrecheckVO vo = new VesselBindPrecheckVO();
		vo.setApplyId(apply.getId());
		vo.setApplyVesselName(apply.getApplyVesselName());
		vo.setApplyVesselImo(apply.getApplyVesselImo());
		vo.setApplyVesselCallSign(apply.getApplyVesselCallSign());

		List<VesselBindVesselOptionVO> candidates = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		// 第一优先：IMO / 呼号。这是「认船不认名」的强身份键 —— 船改名、换旗都不变。
		// 分列采集后各查各的列：IMO 只比 imo_code、呼号只比 call_sign。
		// 此前两列混比（一个输入框填的值同时比两列），会出现「你填的是呼号，
		// 却按 IMO 报重复」的误导性结论。
		if (StringUtils.hasText(imo)) {
			for (VesselInfo vessel : vesselInfoMapper.selectList(
				activeVesselQuery(tenantId).eq(VesselInfo::getImoCode, imo))) {
				addCandidate(candidates, seen, vessel, "IMO一致");
			}
		}
		if (StringUtils.hasText(callSign)) {
			for (VesselInfo vessel : vesselInfoMapper.selectList(
				activeVesselQuery(tenantId).eq(VesselInfo::getCallSign, callSign))) {
				addCandidate(candidates, seen, vessel, "呼号一致");
			}
		}

		// 第二优先：同名在营船舶
		List<VesselInfo> sameName = StringUtils.hasText(name)
			? vesselInfoMapper.selectList(activeVesselQuery(tenantId).eq(VesselInfo::getVesselName, name)) : List.of();
		for (VesselInfo vessel : sameName) {
			addCandidate(candidates, seen, vessel, "船名相同");
		}

		// 第三优先：相近船名。覆盖「申请人把船名写歪了」的常见情形——
		// 原设计只做精确匹配，这是审核员最常撞上的失败点。
		if (StringUtils.hasText(name) && sameName.isEmpty()) {
			// LIMIT 兜住大租户的全表扫描；按创建时间倒序，优先比对近期录入的船
			for (VesselInfo vessel : vesselInfoMapper.selectList(
				activeVesselQuery(tenantId).orderByDesc(VesselInfo::getCreateTime).last("LIMIT 500"))) {
				if (seen.contains(vessel.getId())) {
					continue;
				}
				if (isSimilarVesselName(name, vessel.getVesselName())) {
					addCandidate(candidates, seen, vessel, "船名相近");
				}
			}
		}

		List<VesselBindVesselOptionVO> strong = candidates.stream()
			.filter(c -> "IMO一致".equals(c.getMatchReason()) || "呼号一致".equals(c.getMatchReason()))
			.toList();
		if (!strong.isEmpty()) {
			// 强命中通常唯一，直接预选，审核员核对后点确认即可
			vo.setSuggestedMode("existing");
			vo.setSuggestedVesselId(strong.size() == 1 ? strong.get(0).getId() : null);
			vo.setSuggestion(strong.size() == 1
				? "按申请的 IMO/呼号在系统内找到「" + strong.get(0).getVesselName() + "」，建议直接绑定；请核对船名与申请一致后再通过。"
				: "按申请的 IMO/呼号在系统内找到多条船舶，请人工核实后选择要绑定的那条。");
		}
		else if (!sameName.isEmpty()) {
			vo.setSuggestedMode("existing");
			vo.setSuggestedVesselId(sameName.size() == 1 ? sameName.get(0).getId() : null);
			vo.setSuggestion(sameName.size() == 1
				? "系统内已有同名在营船舶「" + sameName.get(0).getVesselName() + "」，建议绑定它；若确认是另一条船，再选「新建船舶后绑定」。"
				: "系统内存在多条同名在营船舶，请先核实是哪一条，再选择绑定或新建。");
		}
		else if (!candidates.isEmpty()) {
			vo.setSuggestedMode("existing");
			vo.setSuggestion("系统内没有完全同名的在营船舶，但发现相近船名（如「" + candidates.get(0).getVesselName()
				+ "」）。船名可能被写歪，请先核对是否同一条；若确认不是同一条船，请改用「新建船舶后绑定」。");
		}
		else {
			vo.setSuggestedMode("new");
			boolean hasIdentity = StringUtils.hasText(imo) || StringUtils.hasText(callSign);
			vo.setSuggestion(hasIdentity
				? "系统内既没有该 IMO/呼号，也没有「" + name + "」这条船，可直接新建后绑定。"
				: "系统内没有名为「" + name + "」的在营船舶（申请未填 IMO/呼号，仅按船名匹配）。确认是新船可直接新建，否则请先查证船名。");
		}

		vo.setCandidates(candidates);
		return vo;
	}

	@Override
	public List<VesselBindVesselOptionVO> searchVesselOptions(String tenantId, String keyword) {
		LambdaQueryWrapper<VesselInfo> wrapper = activeVesselQuery(tenantId);
		if (StringUtils.hasText(keyword)) {
			String kw = keyword.trim();
			wrapper.and(w -> w.like(VesselInfo::getVesselName, kw).or().like(VesselInfo::getImoCode, kw)
				.or().like(VesselInfo::getCallSign, kw));
		}
		wrapper.orderByDesc(VesselInfo::getCreateTime).last("LIMIT 50");
		return vesselInfoMapper.selectList(wrapper).stream().map(vessel -> {
			VesselBindVesselOptionVO option = new VesselBindVesselOptionVO();
			option.setId(vessel.getId());
			option.setVesselName(vessel.getVesselName());
			option.setImoCode(vessel.getImoCode());
			option.setCallSign(vessel.getCallSign());
			return option;
		}).toList();
	}

	/** 在营船舶查询基线：审核相关的检索一律只看在营船（停用船绑了也无法下单）。 */
	private LambdaQueryWrapper<VesselInfo> activeVesselQuery(String tenantId) {
		return Wrappers.lambdaQuery(VesselInfo.class)
			.eq(VesselInfo::getTenantId, tenantId)
			.eq(VesselInfo::getStatus, VESSEL_STATUS_ACTIVE);
	}

	/** 候选按 ID 去重，同一船只在首次命中的最高优先级原因上出现。 */
	private void addCandidate(List<VesselBindVesselOptionVO> candidates, Set<String> seen, VesselInfo vessel,
			String reason) {
		if (vessel == null || !StringUtils.hasText(vessel.getId()) || !seen.add(vessel.getId())) {
			return;
		}
		VesselBindVesselOptionVO option = new VesselBindVesselOptionVO();
		option.setId(vessel.getId());
		option.setVesselName(vessel.getVesselName());
		option.setImoCode(vessel.getImoCode());
		option.setCallSign(vessel.getCallSign());
		option.setMatchReason(reason);
		candidates.add(option);
	}

	/**
	 * 船名近似判定（仅用于给运营提示，不参与自动绑定）。
	 *
	 * <p>归一化后比较：全角转半角、去空白与常见分隔符、转小写、去掉尾部「号」。
	 * 命中只提示「可能是同一条」，避免船名写法差异导致误建重复档案，但绝不自动选定。
	 */
	private boolean isSimilarVesselName(String left, String right) {
		String a = normalizeVesselName(left);
		String b = normalizeVesselName(right);
		if (a.isEmpty() || b.isEmpty()) {
			return false;
		}
		if (a.equals(b)) {
			return true;
		}
		// 短名不做包含判定：否则「悦航1号」「悦航2号」这类同系列船名会互相误报
		if (a.length() < 3 || b.length() < 3) {
			return false;
		}
		return a.contains(b) || b.contains(a);
	}

	private String normalizeVesselName(String name) {
		if (!StringUtils.hasText(name)) {
			return "";
		}
		StringBuilder sb = new StringBuilder(name.length());
		for (char ch : name.trim().toCharArray()) {
			// 全角字符（含全角空格）统一转半角，再忽略空白、连字符与下划线
			char normalized = ch >= '\uFF01' && ch <= '\uFF5E' ? (char) (ch - 0xFEE0) : ch;
			if (Character.isWhitespace(normalized) || normalized == '-' || normalized == '_' || normalized == '.') {
				continue;
			}
			sb.append(Character.toLowerCase(normalized));
		}
		// 去掉尾部的「号」，让「悦航6」与「悦航6号」视为同一写法
		String result = sb.toString();
		while (result.endsWith("号")) {
			result = result.substring(0, result.length() - 1);
		}
		return result;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public String approveApply(String tenantId, String adminUserId, String applyId, VesselBindAuditDTO dto) {
		VesselBindApply apply = requireApply(tenantId, applyId);
		if (!VesselBindApply.STATUS_PENDING.equals(apply.getStatus())) {
			throw new ArynBusinessException("该申请已处理，不能重复审核");
		}
		ResolveResult resolved = resolveVesselForApprove(tenantId, apply, dto);
		String vesselId = resolved.vesselId();
		// applyRole 理论上必填（submitApply 会兜底成普通船员），但历史数据/直接构造的申请单可能为空；
		// 空角色不能直接落到下面的白名单校验里报「成员角色不合法：null」——那会把「申请单缺字段」
		// 说成「运营传了非法角色」。按提交侧同一口径回落普通船员。
		String requestedRole = dto != null && StringUtils.hasText(dto.getMemberRole()) ? dto.getMemberRole()
				: apply.getApplyRole();
		String role = StringUtils.hasText(requestedRole) ? requestedRole : VesselMemberRole.CREW;
		// 入口层白名单：memberRole 来自请求体，此前传 "1" 就能让申请人直接成为「发起人」
		// （该船最高管理角色），相当于绕过审核拿到成员管理与数据权限。Registrar 内还有兜底。
		if (!VesselMemberRole.isAssignable(role)) {
			throw new ArynBusinessException("成员角色不合法：" + role);
		}

		// 跨船绑定（申请船名与目标船身份键全不命中）时，bind_reason 必须是运营填写的审核意见：
		// 这正是「为什么把 8 号绑到 7 号」的答案，事后复核靠它。
		String bindReason = VesselMemberBindSource.APPLY_AUDIT_MISMATCH.equals(resolved.bindSource())
			? trimToNull(dto == null ? null : dto.getAuditRemark())
			: "绑定申请审核通过（申请单 " + apply.getApplyNo() + "）";
		memberRegistrar.ensureOnboard(tenantId, vesselId, apply.getUserId(), role, FLAG_YES,
				VesselMemberRole.CONFIRMER.equals(role) ? FLAG_YES : FLAG_NO, bindReason, resolved.bindSource());

		apply.setStatus(VesselBindApply.STATUS_APPROVED);
		apply.setMatchedVesselId(vesselId);
		apply.setAuditBy(adminUserId);
		apply.setAuditTime(LocalDateTime.now());
		if (dto != null) {
			apply.setAuditRemark(dto.getAuditRemark());
		}
		vesselBindApplyMapper.updateById(apply);
		log.info("绑定申请审核通过 tenant={} apply={} vessel={} user={} role={} bindSource={}", tenantId, applyId,
				vesselId, apply.getUserId(), role, resolved.bindSource());
		// 通知延后到事务提交后：回滚了不该冒出「审核通过」的站内信
		TransactionalMqUtils.sendAfterCommit(() -> notifyApplyResult(apply, true, dto, role));
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
		log.info("绑定申请驳回 tenant={} apply={} reason={}", tenantId, applyId, auditRemark);
		// 驳回同样要通知：申请人此前只能自己反复打开小程序看状态，等于石沉大海
		TransactionalMqUtils.sendAfterCommit(() -> notifyApplyResult(apply, false, null, null));
	}

	/**
	 * 审核结果通知申请人（站内信）。
	 *
	 * <p>通知不参与业务成败：任何异常只告警。幂等由站内信 (source_type, source_key) 兜底，
	 * eventId 带申请单主键，重复审核不会重复入箱。
	 *
	 * <p><b>必须在事务提交后调用</b>（{@link TransactionalMqUtils#sendAfterCommit}）：
	 * 事务内直接发，回滚后会留下一条「审核通过」的误报。
	 *
	 * <p><b>跨船绑定时必须说清两条船</b>：此前通知统一写成「您申请的「{实际绑定船}」已绑定成功」，
	 * 把实际绑定的船冒充成用户申请的船——用户申请「悦航8号」、实际绑了「悦航7号」时，
	 * 通知反而坐实了这个错误，用户看不出异常。现在不一致时明确给出「申请的是什么、实际绑的是哪条、原因」。
	 */
	private void notifyApplyResult(VesselBindApply apply, boolean approved, VesselBindAuditDTO dto, String role) {
		try {
			String vesselName = null;
			if (approved && StringUtils.hasText(apply.getMatchedVesselId())) {
				VesselInfo vessel = vesselInfoMapper.selectById(apply.getMatchedVesselId());
				vesselName = vessel == null ? null : vessel.getVesselName();
			}
			boolean sales = VesselBindApply.ROLE_SALES.equals(apply.getApplyRole());
			String applicant = sales ? "业务员" : "船员";
			String roleText = roleText(role);
			String applyVesselName = displayOr(apply.getApplyVesselName(), "未填写船名");
			// 只有拿到了实际绑定的船名才谈得上「是否不一致」；查不到船名时退回旧文案（不含误导性对照）
			boolean mismatch = approved && StringUtils.hasText(vesselName)
					&& Boolean.TRUE.equals(vesselNameMismatch(apply.getApplyVesselName(), vesselName));

			String title = approved ? "船舶绑定申请已通过" : "船舶绑定申请未通过";
			String summary;
			StringBuilder content = new StringBuilder();
			if (approved && mismatch) {
				summary = "您申请的「" + applyVesselName + "」已通过审核，实际绑定「" + vesselName + "」";
				content.append("您申请的「").append(applyVesselName).append("」已通过审核，实际绑定「")
					.append(vesselName).append("」。");
				if (StringUtils.hasText(apply.getAuditRemark())) {
					content.append("原因：").append(apply.getAuditRemark()).append("。");
				}
			}
			else if (approved) {
				String boundName = displayOr(vesselName, apply.getApplyVesselName());
				summary = "您申请的「" + boundName + "」已绑定成功";
				content.append("您的").append(applicant).append("绑定申请（申请单 ").append(apply.getApplyNo())
					.append("）已通过审核，已加入「").append(boundName).append("」。");
				if (StringUtils.hasText(roleText)) {
					content.append("您在本船的角色为：").append(roleText).append("。");
				}
				content.append("现在可以在小程序内采购并选择该船的配送计划。");
			}
			else {
				summary = "您申请的「" + applyVesselName + "」未通过审核";
				content.append("您的").append(applicant).append("绑定申请（申请单 ").append(apply.getApplyNo())
					.append("）未通过审核。");
				if (StringUtils.hasText(apply.getAuditRemark())) {
					content.append("原因：").append(apply.getAuditRemark()).append("。");
				}
				content.append("可补充船名或 IMO/呼号后重新提交，或联系运营协助。");
			}
			if (approved && mismatch && StringUtils.hasText(roleText)) {
				content.append("您在本船的角色为：").append(roleText).append("。");
			}

			MessageSendCommand command = new MessageSendCommand();
			command.setEventId("vessel-bind-audit:" + (approved ? "approved:" : "rejected:") + apply.getId());
			command.setTenantId(apply.getTenantId());
			command.setRecipientType(MessageIdentityType.MALL_USER.name());
			command.setRecipientId(apply.getUserId());
			command.setCategory(CATEGORY_BIND_AUDIT);
			command.setTitle(title);
			command.setSummary(summary);
			command.setContent(content.toString());
			command.setBizType(BIZ_TYPE_BIND_APPLY);
			command.setBizId(apply.getId());
			// jumpPayload 落 json 列，必须包成 JSON 对象，裸串会被消费端校验拒绝
			command.setJumpPayload(objectMapper.writeValueAsString(
				Map.of(MessageJumpConstants.ROUTE, BIND_PAGE_ROUTE)));
			command.setChannels(List.of("IN_APP"));
			rocketMQTemplate.convertAndSend(RocketMqConstants.MESSAGE_SEND_COMMAND_TOPIC, command);
		}
		catch (Exception ex) {
			log.warn("绑定申请结果通知发送失败 apply={} approved={}: {}", apply.getId(), approved, ex.getMessage());
		}
	}

	private String displayOr(String value, String fallback) {
		return StringUtils.hasText(value) ? value : fallback;
	}

	/** 去首尾空白；空白串视为「未填」，统一返回空串便于 StringUtils.hasText 判断 */
	private String trimOrEmpty(String value) {
		return StringUtils.hasText(value) ? value.trim() : "";
	}

	/** 去首尾空白；空白串返回 null（落库语义：未填即 NULL，配合唯一键的 NULL 允许多值） */
	private String trimToNull(String value) {
		return StringUtils.hasText(value) ? value.trim() : null;
	}

	private String roleText(String role) {
		if (role == null) {
			return null;
		}
		return switch (role) {
			case VesselMember.ROLE_OWNER -> "发起人";
			case VesselMember.ROLE_CREW -> "普通船员";
			case VesselMember.ROLE_CONFIRMER -> "采购确认人";
			case VesselMember.ROLE_SALES -> "商城业务员";
			default -> null;
		};
	}

	// =====================================================================
	// 内部方法
	// =====================================================================

	/**
	 * 审核通过时确定目标船舶 + 该次绑定的来源标记。
	 *
	 * <p>三条路径：优先用显式指定的已有船，其次按新船信息创建，
	 * 最后回落到「先按 IMO/呼号、再按船名精确匹配」。
	 *
	 * <p><b>显式指定已有船时的跨船一致性门槛（本次安全加固的核心）</b>：
	 * 此前这里对申请单的船一个字都不比，运营在弹窗里选中哪条就绑哪条——
	 * 「悦航8号」的申请被绑到「悦航7号」就是这样发生的，全程零提示零留痕。
	 * 现在只有「客观身份键命中」才放行，否则必须运营显式确认 + 填写理由。
	 */
	private ResolveResult resolveVesselForApprove(String tenantId, VesselBindApply apply, VesselBindAuditDTO dto) {
		if (dto != null && StringUtils.hasText(dto.getMatchedVesselId())) {
			VesselInfo target = requireVesselActive(tenantId, dto.getMatchedVesselId());
			boolean matched = isApplyIdentityMatched(apply, target);
			if (matched) {
				return new ResolveResult(target.getId(), VesselMemberBindSource.APPLY_AUDIT);
			}
			// 双强键（IMO/呼号）都没命中、船名归一化后也不同：可能是船改名/换旗/租船，也可能是绑错船。
			// 系统无法分辨，只能要求人工背书——确认 + 必填理由，事后按 APPLY_AUDIT_MISMATCH 来源可筛出来复核。
			if (!Boolean.TRUE.equals(dto.getConfirmVesselMismatch())) {
				throw new ArynBusinessException("申请船名「" + displayOr(apply.getApplyVesselName(), "未填写")
					+ "」与所选船舶「" + displayOr(target.getVesselName(), target.getId())
					+ "」不一致。请确认确实是同一条船后勾选确认，并填写审核意见说明原因");
			}
			if (!StringUtils.hasText(dto.getAuditRemark())) {
				throw new ArynBusinessException("申请船名与所选船舶不一致时，必须填写审核意见说明原因");
			}
			log.warn("审核跨船绑定（申请船与目标船身份键均不命中，运营已确认） tenant={} apply={} vessel={}", tenantId,
					apply.getApplyNo(), target.getId());
			return new ResolveResult(target.getId(), VesselMemberBindSource.APPLY_AUDIT_MISMATCH);
		}
		String newName = dto != null && StringUtils.hasText(dto.getNewVesselName()) ? dto.getNewVesselName()
				: null;
		if (newName == null) {
			VesselInfo matched = matchActiveVesselForApply(tenantId, apply);
			if (matched != null) {
				return new ResolveResult(matched.getId(), VesselMemberBindSource.APPLY_AUDIT);
			}
			// 匹配不到时把「为什么没匹配上」说清楚：申请未填 IMO/呼号时只能按船名比，
			// 审核员据此知道该补什么信息，而不是只看到一句「没有这条船」
			boolean hasIdentity = StringUtils.hasText(apply.getApplyVesselImo())
					|| StringUtils.hasText(apply.getApplyVesselCallSign());
			String hint = hasIdentity
				? "已按申请的 IMO/呼号与船名「" + apply.getApplyVesselName() + "」查找，均未命中在营船舶"
				: "申请未填 IMO/呼号，仅按船名「" + apply.getApplyVesselName() + "」精确查找，未命中在营船舶";
			throw new ArynBusinessException(hint + "；请改用「绑定已有船舶」或「新建船舶后绑定」后通过");
		}

		String trimmed = newName.trim();
		// 同名在营船舶已存在时默认拒绝：船名没有唯一约束，放行会静默产出重复档案，
		// 成员、靠港、订单随后被拆到两条船上，而系统没有任何合并工具。
		Long sameNameCount = vesselInfoMapper.selectCount(activeVesselQuery(tenantId)
			.eq(VesselInfo::getVesselName, trimmed));
		if (sameNameCount != null && sameNameCount > 0 && !Boolean.TRUE.equals(dto.getConfirmDuplicateName())) {
			throw new ArynBusinessException("系统内已存在同名在营船舶「" + trimmed
				+ "」。若确认是另一条船，请勾选「确认新建同名船舶」后重试；否则请改用「绑定已有船舶」");
		}
		// IMO 是唯一键，撞上说明系统内几乎肯定是同一条船，不给出「强制新建」的口子。
		// 注意这里不过滤 status：唯一键跨停用船同样生效，停用船建不进去，只能让运营去核实
		if (dto != null && StringUtils.hasText(dto.getNewVesselImo())) {
			Long imoCount = vesselInfoMapper.selectCount(Wrappers.lambdaQuery(VesselInfo.class)
				.eq(VesselInfo::getTenantId, tenantId)
				.eq(VesselInfo::getImoCode, dto.getNewVesselImo().trim()));
			if (imoCount != null && imoCount > 0) {
				throw new ArynBusinessException("IMO 编号「" + dto.getNewVesselImo().trim()
					+ "」在系统内已存在，这条船很可能已经录入：请在船舶档案中核实后改用「绑定已有船舶」；"
					+ "若该船已停用，需先启用或更正原档案的 IMO 后再处理");
			}
		}
		// 呼号不是唯一键（同租户内理论上不应重复，但真实数据里存在换旗/临时呼号），
		// 不硬拦、只提示：强拦会挡住「这确实是另一条船」的合法建档。
		if (dto != null && StringUtils.hasText(dto.getNewVesselCallSign())) {
			Long callSignCount = vesselInfoMapper.selectCount(Wrappers.lambdaQuery(VesselInfo.class)
				.eq(VesselInfo::getTenantId, tenantId)
				.eq(VesselInfo::getCallSign, dto.getNewVesselCallSign().trim()));
			if (callSignCount != null && callSignCount > 0) {
				log.warn("审核建船的呼号已存在于其他船舶 tenant={} callSign={} apply={}", tenantId,
						dto.getNewVesselCallSign().trim(), apply.getApplyNo());
			}
		}

		// 船还没录入系统 —— 先建船再绑定；申请里能确认的信息（呼号、常靠港）一并落进档案，
		// 避免「审核建出的船只有名称和 IMO」还要运营二次补录
		VesselInfo vessel = new VesselInfo();
		vessel.setVesselName(trimmed);
		// 与原值一致：查重时用 trim 后的值，落库却存未 trim 的原值，会产出
		// 「查重通过、实际存了带空格的值」的脏档案，且后续按 IMO 匹配全部失配
		vessel.setImoCode(trimToNull(dto.getNewVesselImo()));
		vessel.setCallSign(trimToNull(dto.getNewVesselCallSign()));
		// 船型必须校验：此前恒落 "5"，且 DTO 注释那套旧口径会误导调用方写错码值
		String requestedType = trimToNull(dto.getNewVesselType());
		if (requestedType != null && !VesselType.isValid(requestedType)) {
			throw new ArynBusinessException("船舶类型不合法：" + requestedType);
		}
		vessel.setVesselType(requestedType == null ? VesselType.DEFAULT : requestedType);
		vessel.setStatus(VESSEL_STATUS_ACTIVE);
		StringBuilder remark = new StringBuilder("由绑定申请审核创建（申请单 ").append(apply.getApplyNo()).append("）");
		if (StringUtils.hasText(apply.getApplyPortName())) {
			// 档案表没有常靠港字段，先随备注留存；运营维护靠港计划时可作为参照
			remark.append("；申请人常靠港：").append(apply.getApplyPortName());
		}
		vessel.setRemark(remark.toString());
		vessel.setTenantId(tenantId);
		vesselInfoMapper.insert(vessel);
		log.info("审核通过时新建船舶 tenant={} vessel={} name={}", tenantId, vessel.getId(), vessel.getVesselName());
		// 新建船舶是按申请信息建出来的，船名/IMO/呼号都取自申请单，不存在跨船偏离
		return new ResolveResult(vessel.getId(), VesselMemberBindSource.APPLY_AUDIT);
	}

	/**
	 * 申请单与目标船舶是否「可客观判定为同一条船」。
	 *
	 * <p>判据是身份键而非船名（契约 2.1）：船改名、换旗、租船、申请人写法差异都会让船名不同，
	 * 但 IMO 是终身不变的国际海事组织编号、呼号次之；两者任一一致就不该拦。
	 * 只有双强键都不命中（或申请没填），才退到船名归一化比较。
	 *
	 * <p>强键必须「两边都非空」才算命中：申请单没填 IMO、目标船也没填 IMO，
	 * 不能用两个空串相等来放行——那会把「什么都不知道」当成「确认是同一条船」。
	 */
	private boolean isApplyIdentityMatched(VesselBindApply apply, VesselInfo target) {
		if (target == null) {
			return false;
		}
		String applyImo = trimToNull(apply.getApplyVesselImo());
		String targetImo = trimToNull(target.getImoCode());
		if (applyImo != null && applyImo.equals(targetImo)) {
			return true;
		}
		String applyCallSign = trimToNull(apply.getApplyVesselCallSign());
		String targetCallSign = trimToNull(target.getCallSign());
		if (applyCallSign != null && applyCallSign.equals(targetCallSign)) {
			return true;
		}
		// 归一化比较：全角转半角、去空白/分隔符、转小写、去尾部「号」——
		// 「悦航 8 号」与「悦航8号」是同一个船名写法，不该被当成跨船绑定
		String applyName = normalizeVesselName(apply.getApplyVesselName());
		return !applyName.isEmpty() && applyName.equals(normalizeVesselName(target.getVesselName()));
	}

	/** 审核通过的解析结果：目标船 + 该次绑定的来源标记（决定 bind_source / 是否需要偏离理由） */
	private record ResolveResult(String vesselId, String bindSource) {
	}

	/**
	 * 审核「自动匹配」的命中顺序：先 IMO、再呼号、最后船名精确匹配。
	 *
	 * <p>IMO 是船终身的强身份键，优先级最高；呼号次之（换旗/租船时会变，但仍强于船名）；
	 * 船名最弱（无唯一约束，改名、写法差异都会失配）。分列采集后各查各的列——
	 * 此前一个输入框同时比两列，会造成「填的是呼号却按 IMO 报错」的误导。
	 */
	private VesselInfo matchActiveVesselForApply(String tenantId, VesselBindApply apply) {
		String imo = trimOrEmpty(apply.getApplyVesselImo());
		if (StringUtils.hasText(imo)) {
			VesselInfo byImo = vesselInfoMapper.selectOne(
				activeVesselQuery(tenantId).eq(VesselInfo::getImoCode, imo).last("LIMIT 1"));
			if (byImo != null) {
				return byImo;
			}
		}
		String callSign = trimOrEmpty(apply.getApplyVesselCallSign());
		if (StringUtils.hasText(callSign)) {
			VesselInfo byCallSign = vesselInfoMapper.selectOne(
				activeVesselQuery(tenantId).eq(VesselInfo::getCallSign, callSign).last("LIMIT 1"));
			if (byCallSign != null) {
				return byCallSign;
			}
		}
		return vesselInfoMapper.selectOne(activeVesselQuery(tenantId)
			.eq(VesselInfo::getVesselName, apply.getApplyVesselName())
			.last("LIMIT 1"));
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

	/**
	 * 校验船舶存在且在营，并返回档案。
	 *
	 * <p>返回实体而不是 void：审核通过的一致性判定要拿目标船的 IMO/呼号/船名与申请单比对，
	 * 单独再查一次会多一次往返、也可能读到不一致的快照。既有调用方直接忽略返回值即可。
	 */
	private VesselInfo requireVesselActive(String tenantId, String vesselId) {
		VesselInfo vessel = vesselInfoMapper.selectOne(Wrappers.lambdaQuery(VesselInfo.class)
			.eq(VesselInfo::getTenantId, tenantId)
			.eq(VesselInfo::getId, vesselId)
			.eq(VesselInfo::getStatus, VESSEL_STATUS_ACTIVE));
		if (vessel == null) {
			throw new ArynBusinessException("船舶不存在或已停用");
		}
		return vessel;
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
		Set<String> activeMemberKeys = loadActiveMemberKeys(applies);
		return applies.stream()
			.map(apply -> {
				VesselBindApplyVO vo = toApplyVO(apply, users);
				if (StringUtils.hasText(apply.getMatchedVesselId())) {
					String matchedName = vesselNames.get(apply.getMatchedVesselId());
					vo.setMatchedVesselName(matchedName);
					vo.setMemberActive(activeMemberKeys.contains(memberKey(apply.getUserId(), apply.getMatchedVesselId())));
					// 申请船名 vs 实际绑定船名：不一致要在列表上可见。
					// 误绑事故里用户在「我的申请」只看到「已通过」，看不到自己申请的船其实没绑上，
					// 运营也看不出这条申请曾经跨船。仅在有最终绑定船名时判定（未通过为 null）。
					vo.setVesselMismatch(vesselNameMismatch(apply.getApplyVesselName(), matchedName));
				}
				return vo;
			})
			.toList();
	}

	/**
	 * 申请船名与最终绑定船名是否不一致（归一化后比较）。
	 *
	 * <p>归一化复用审核侧的同一套规则（全角转半角、去空白/分隔符、去尾部「号」）：
	 * 判定口径必须与「审核时是否放行」完全一致，否则会出现
	 * 「审核按归一化放行、列表却标记为不一致」的自相矛盾。比较不出结果（任一方为空）时返回 null。
	 */
	private Boolean vesselNameMismatch(String applyVesselName, String matchedVesselName) {
		if (!StringUtils.hasText(applyVesselName) || !StringUtils.hasText(matchedVesselName)) {
			return null;
		}
		return !normalizeVesselName(applyVesselName).equals(normalizeVesselName(matchedVesselName));
	}

	/**
	 * 批量取「仍在该船在船」的 (user, vessel) 组合，供列表渲染解绑按钮用。
	 *
	 * <p>一次 in 查询覆盖整页，避免逐行查库；查询失败降级为全部视为在船
	 * （宁可让按钮可点后报错，也不要因为查询失败而让运营看不到入口）。
	 */
	private Set<String> loadActiveMemberKeys(List<VesselBindApply> applies) {
		List<VesselBindApply> withVessel = applies.stream()
			.filter(apply -> StringUtils.hasText(apply.getMatchedVesselId()) && StringUtils.hasText(apply.getUserId()))
			.toList();
		if (withVessel.isEmpty()) {
			return Set.of();
		}
		List<String> vesselIds = withVessel.stream().map(VesselBindApply::getMatchedVesselId).distinct().toList();
		try {
			return vesselMemberMapper.selectList(Wrappers.lambdaQuery(VesselMember.class)
				.in(VesselMember::getVesselId, vesselIds)
				.eq(VesselMember::getStatus, VesselMember.STATUS_ONBOARD))
				.stream()
				.map(member -> memberKey(member.getUserId(), member.getVesselId()))
				.collect(java.util.stream.Collectors.toSet());
		}
		catch (Exception ex) {
			log.warn("在船状态批量回填失败，解绑按钮按可用渲染：{}", ex.getMessage());
			return withVessel.stream()
				.map(apply -> memberKey(apply.getUserId(), apply.getMatchedVesselId()))
				.collect(java.util.stream.Collectors.toSet());
		}
	}

	private String memberKey(String userId, String vesselId) {
		return userId + "@" + vesselId;
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
		vo.setApplyVesselCallSign(apply.getApplyVesselCallSign());
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
		// 来源标签：管理端成员列表靠它区分「审核通过」「邀请码」与「分享链接自动补建」，
		// 是运营识别并回收低信任关系的唯一依据，必须原样下发（含中文名，前端不必再维护一份字典）。
		vo.setBindSource(member.getBindSource());
		vo.setBindSourceLabel(VesselMemberBindSource.label(member.getBindSource()));
		vo.setBindReason(member.getBindReason());
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
