package com.aryn.cloud.vessel.support;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.vessel.api.constant.VesselMemberBindSource;
import com.aryn.cloud.vessel.api.constant.VesselMemberRole;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.aryn.cloud.vessel.api.entity.VesselMemberAuditLog;
import com.aryn.cloud.vessel.mapper.VesselMemberAuditLogMapper;
import com.aryn.cloud.vessel.mapper.VesselMemberMapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 成员关系落库的唯一入口：新建 / 复活 / 角色变更三选一。
 *
 * <p><b>为什么必须有这一层</b>：{@code uk_vessel_member_unique(tenant_id, vessel_id, user_id)}
 * 不含 {@code del_flag}，而退出船舶、被解绑走的都是逻辑删除——历史行仍占着键位。
 * 任何一处「加入船舶」直接 insert，在「退过船/被解绑过」的用户身上必然撞键 1062，
 * 表现为「加入失败」而日志里是一句主键冲突。五个入口（审核通过、邀请码、管理端绑定、
 * 业务员现场拉人、分享链接补建）此前各写各的 insert，这个坑会散落五处，因此收敛到这里，
 * 语义与共享购物车的加行 upsert 一致：无行新建、已移除复活、有效行按需更新角色。
 *
 * <p><b>2026-10-19 安全加固后本类额外承担三件横切职责</b>（放在这里而不是各入口，
 * 因为五条写入通道最终都过它，任何一处单独校验都会被新增入口漏掉）：
 * <ol>
 *   <li><b>角色白名单兜底</b>：写库前拒绝非可分配角色（{@link VesselMemberRole#ASSIGNABLE}），
 *       堵住「请求体传 memberRole=1 就变成发起人」的越权面；</li>
 *   <li><b>绑定来源必填</b>：落库/复活的 {@code bind_source} 由调用方显式给出，
 *       未给按 {@code MANUAL} 兜底，避免再出现「来源静默为空、无法按来源回收」；</li>
 *   <li><b>写审计</b>：BIND / REBIND / ROLE_CHANGE 一一留痕，事后能回答
 *       「谁在什么时候把谁绑到哪条船、为什么」——这是误绑事故里此前完全缺失的证据链。</li>
 * </ol>
 *
 * @author aryn
 * @since 2026/10/16
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VesselMemberRegistrar {

	private final VesselMemberMapper vesselMemberMapper;

	/** 审计留痕（追加写）。构造器注入：本类是唯一落库入口，审计不能做成可选的旁路 */
	private final VesselMemberAuditLogMapper vesselMemberAuditLogMapper;

	/**
	 * 结果：决定调用方日志与返回值。
	 */
	public enum Outcome {

		/** 该用户本来就在船上、角色与权限位也一致，未改动任何数据 */
		ALREADY_ONBOARD,

		/** 该用户本来就在船上，但角色/权限位与本次要求不同，已就地更新并留痕 */
		ROLE_UPDATED,

		/** 复用了被移除的历史行 */
		REVIVED,

		/** 全新插入 */
		CREATED

	}

	/**
	 * 落库结果。带上实体，避免调用方为了拿 ID/加入时间再查一次库。
	 */
	public record OnboardResult(Outcome outcome, VesselMember member) {
	}

	/**
	 * 确保成员关系有效。已有效则按需更新角色，被移除过则复活，否则新建。
	 *
	 * @param canEdit 可编辑共享购物车
	 * @param canConfirm 可确认提交订单
	 * @param remark 业务备注（如「通过邀请码加入」）；新建/复活时同时作为 {@code bind_reason}
	 * @param bindSource 绑定来源，取值见 {@link VesselMemberBindSource}；为空时按 {@code MANUAL} 兜底
	 * @throws ArynBusinessException 角色不在 {@link VesselMemberRole#ASSIGNABLE} 白名单内
	 */
	public OnboardResult ensureOnboard(String tenantId, String vesselId, String userId, String memberRole,
			String canEdit, String canConfirm, String remark, String bindSource) {
		// 白名单兜底：入口层的校验可能被将来新增的写入通道漏掉，这里是「发起人不可被授予」的最后一道闸。
		// 放在最前面（早于任何查询）——非法角色不必浪费一次 SELECT，也保证任何路径都不会写库。
		if (!VesselMemberRole.isAssignable(memberRole)) {
			throw new ArynBusinessException("成员角色不合法：" + memberRole);
		}
		String source = StringUtils.hasText(bindSource) ? bindSource : VesselMemberBindSource.MANUAL;

		Long active = vesselMemberMapper.selectCount(Wrappers.lambdaQuery(VesselMember.class)
			.eq(VesselMember::getTenantId, tenantId)
			.eq(VesselMember::getVesselId, vesselId)
			.eq(VesselMember::getUserId, userId));
		if (active != null && active > 0) {
			VesselMember existing = vesselMemberMapper.selectOne(Wrappers.lambdaQuery(VesselMember.class)
				.eq(VesselMember::getTenantId, tenantId)
				.eq(VesselMember::getVesselId, vesselId)
				.eq(VesselMember::getUserId, userId));
			if (existing == null) {
				// 计数命中却取不到行：并发删除等极端情况。不改成 insert（会撞唯一键），
				// 保持原语义由调用方按「已在船上」处理。
				log.warn("成员关系计数与查询不一致 tenant={} vessel={} user={}", tenantId, vesselId, userId);
				return new OnboardResult(Outcome.ALREADY_ONBOARD, null);
			}
			// 此前对已存在成员直接返回「已在船上」，导致申请单记的是「采购确认人」、
			// 库里仍是「普通船员」，而站内信按申请角色告知用户——三方口径不一致。
			// 现在角色不同就就地更新并写 ROLE_CHANGE 留痕（契约 3.4：只比角色；
			// 权限位差异不单独触发更新，避免同一角色反复调用把行改来改去）。
			boolean roleChanged = !Objects.equals(existing.getMemberRole(), memberRole);
			if (!roleChanged) {
				return new OnboardResult(Outcome.ALREADY_ONBOARD, existing);
			}
			String oldRole = existing.getMemberRole();
			existing.setMemberRole(memberRole);
			existing.setCanEdit(canEdit);
			existing.setCanConfirm(canConfirm);
			// 刻意不覆盖 bind_source / bind_reason：来源要回答的是「最初怎么进来的」，
			// 后续换角色不该把它改写成这次通道，否则来源与审计会互相矛盾。
			vesselMemberMapper.updateById(existing);
			writeAudit(tenantId, vesselId, userId, VesselMemberAuditLog.ACTION_ROLE_CHANGE, source, oldRole, memberRole,
					remark);
			log.info("成员角色变更 tenant={} vessel={} user={} role={}->{} source={}", tenantId, vesselId, userId,
					oldRole, memberRole, source);
			return new OnboardResult(Outcome.ROLE_UPDATED, existing);
		}

		// 先试复活：唯一键不含 del_flag，有历史行时 insert 一定撞键
		LocalDateTime now = LocalDateTime.now();
		if (vesselMemberMapper.reviveRemovedMember(tenantId, vesselId, userId, memberRole, canEdit, canConfirm, now,
				remark, source, remark) > 0) {
			writeAudit(tenantId, vesselId, userId, VesselMemberAuditLog.ACTION_REBIND, source, null, memberRole, remark);
			log.info("成员关系复活（该用户此前退出或被解绑） tenant={} vessel={} user={} role={} source={}", tenantId,
					vesselId, userId, memberRole, source);
			return new OnboardResult(Outcome.REVIVED,
					vesselMemberMapper.selectOne(Wrappers.lambdaQuery(VesselMember.class)
						.eq(VesselMember::getTenantId, tenantId)
						.eq(VesselMember::getVesselId, vesselId)
						.eq(VesselMember::getUserId, userId)));
		}

		VesselMember member = new VesselMember();
		member.setVesselId(vesselId);
		member.setUserId(userId);
		member.setMemberRole(memberRole);
		member.setCanEdit(canEdit);
		member.setCanConfirm(canConfirm);
		member.setStatus(VesselMember.STATUS_ONBOARD);
		member.setJoinTime(now);
		member.setRemark(remark);
		member.setBindSource(source);
		member.setBindReason(remark);
		member.setTenantId(tenantId);
		vesselMemberMapper.insert(member);
		writeAudit(tenantId, vesselId, userId, VesselMemberAuditLog.ACTION_BIND, source, null, memberRole, remark);
		return new OnboardResult(Outcome.CREATED, member);
	}

	/**
	 * 写一条审计。
	 *
	 * <p>刻意吞异常只告警：审计表迁移的灰度窗口里，不能让「审计写不进去」把绑定/换角色整体弄失败
	 * ——业务可用性优先；代价是可能缺几条审计，故必须 WARN 留可排查线索。
	 *
	 * <p>operatorId / operatorName 留空：本方法的契约签名（3.4）不含操作人，自助通道（邀请码、
	 * 分享链接、本人操作）也确实没有「操作人」概念；管理端解绑这类有明确操作人的动作，
	 * 由调用方（{@code VesselBindServiceImpl#adminUnbindMember}）直接写带 operator 的审计行。
	 */
	private void writeAudit(String tenantId, String vesselId, String userId, String action, String bindSource,
			String oldRole, String newRole, String reason) {
		try {
			VesselMemberAuditLog audit = new VesselMemberAuditLog();
			// 与 VesselServiceImpl 写 VesselCallChangeLog 同口径：主键/时间/逻辑删除显式赋值。
			// 虽然线上有 MyMetaObjectHandler 自动填充，但纯单测环境没有 MyBatis 容器，
			// 依赖填充会让「审计是否写入正确」无法断言；显式赋值也让灰度期缺 handler 时不会插出空值。
			audit.setId(IdWorker.getIdStr());
			audit.setTenantId(tenantId);
			audit.setVesselId(vesselId);
			audit.setUserId(userId);
			audit.setAction(action);
			audit.setBindSource(bindSource);
			audit.setOldMemberRole(oldRole);
			audit.setNewMemberRole(newRole);
			// reason 落 varchar(500)：业务备注可能更长，截断而不是让整条审计写失败
			audit.setReason(truncate(reason, 500));
			audit.setCreateTime(LocalDateTime.now());
			audit.setDelFlag("0");
			vesselMemberAuditLogMapper.insert(audit);
		}
		catch (Exception ex) {
			log.warn("成员审计写入失败（不阻断业务） tenant={} vessel={} user={} action={}: {}", tenantId, vesselId,
					userId, action, ex.getMessage());
		}
	}

	private String truncate(String value, int maxLength) {
		if (value == null || value.length() <= maxLength) {
			return value;
		}
		return value.substring(0, maxLength);
	}

}
