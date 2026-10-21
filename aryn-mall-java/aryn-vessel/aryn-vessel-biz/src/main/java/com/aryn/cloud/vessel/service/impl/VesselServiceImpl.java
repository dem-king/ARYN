package com.aryn.cloud.vessel.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.order.api.remote.RemoteShoppingCartService;
import com.aryn.cloud.vessel.api.constant.VesselMemberBindSource;
import com.aryn.cloud.vessel.api.constant.VesselMemberRole;
import com.aryn.cloud.vessel.api.constant.VesselType;
import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.entity.VesselCall;
import com.aryn.cloud.vessel.api.entity.VesselInfo;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.aryn.cloud.vessel.mapper.VesselCallMapper;
import com.aryn.cloud.vessel.mapper.VesselInfoMapper;
import com.aryn.cloud.vessel.mapper.VesselMemberMapper;
import com.aryn.cloud.vessel.service.VesselService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 船舶与靠港计划服务实现。
 *
 * <p>所有查询显式携带 tenantId：即便脱离租户拦截器（单元测试/远程调用）也不产生跨租户读写；
 * C 端读取一律先校验在船成员关系。
 *
 * @author aryn
 * @since 2026/9/11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VesselServiceImpl implements VesselService {

	/** 在船状态 */
	private static final String MEMBER_STATUS_ONBOARD = "1";

	/** 船舶在营 */
	private static final String VESSEL_STATUS_ACTIVE = "1";

	/** 靠港状态：1计划中 2靠泊中 3已完成 4已取消 */
	private static final String CALL_STATUS_PLANNED = "1";

	private static final String CALL_STATUS_BERTHED = "2";

	private static final String CALL_STATUS_COMPLETED = "3";

	private static final String CALL_STATUS_CANCELED = "4";

	/** 海员申报去重窗口：同船 ETA 相差在此范围内视为同一航次 */
	private static final long DECLARE_DEDUP_HOURS = 72L;

	private final VesselInfoMapper vesselInfoMapper;

	private final VesselMemberMapper vesselMemberMapper;

	private final VesselCallMapper vesselCallMapper;

	private final com.aryn.cloud.vessel.mapper.VesselCallChangeLogMapper vesselCallChangeLogMapper;

	private final org.apache.rocketmq.spring.core.RocketMQTemplate rocketMQTemplate;

	/** 购物车归属迁移（shopping_cart 在 order 库）：靠港生效后把该船无归属/挂已结束靠港的行顺延。
	 *  刻意非构造器注入：远程接口的引用代理与本地实现 bean 双候选，构造器注入会炸 boot 启动 */
	@DubboReference
	private RemoteShoppingCartService remoteShoppingCartService;

	/** 成员关系落库统一入口（新建/复活）。字段注入与上面同理：避免改动既有构造器签名波及单测 */
	@org.springframework.beans.factory.annotation.Autowired
	private com.aryn.cloud.vessel.support.VesselMemberRegistrar memberRegistrar;

	@Override
	public IPage<VesselInfo> pageVessels(String tenantId, IPage<VesselInfo> page, VesselInfo query) {
		LambdaQueryWrapper<VesselInfo> wrapper = Wrappers.lambdaQuery(VesselInfo.class)
				.eq(VesselInfo::getTenantId, tenantId)
				.eq(StringUtils.hasText(query.getStatus()), VesselInfo::getStatus, query.getStatus())
				.like(StringUtils.hasText(query.getVesselName()), VesselInfo::getVesselName, query.getVesselName())
				.like(StringUtils.hasText(query.getImoCode()), VesselInfo::getImoCode, query.getImoCode())
				.orderByDesc(VesselInfo::getCreateTime);
		return vesselInfoMapper.selectPage(page, wrapper);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VesselInfo saveVessel(String tenantId, VesselInfo vessel) {
		normalizeVessel(vessel);
		if (!StringUtils.hasText(vessel.getVesselName())) {
			throw new ArynBusinessException("船舶名称不能为空");
		}
		if (StringUtils.hasText(vessel.getImoCode())) {
			Long duplicated = vesselInfoMapper.selectCount(Wrappers.lambdaQuery(VesselInfo.class)
					.eq(VesselInfo::getTenantId, tenantId)
					.eq(VesselInfo::getImoCode, vessel.getImoCode()));
			if (duplicated != null && duplicated > 0) {
				throw new ArynBusinessException("同一租户下 IMO 编号已存在");
			}
		}
		vessel.setId(null);
		vessel.setTenantId(tenantId);
		if (!StringUtils.hasText(vessel.getStatus())) {
			vessel.setStatus(VESSEL_STATUS_ACTIVE);
		}
		vesselInfoMapper.insert(vessel);
		return vessel;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VesselInfo updateVessel(String tenantId, VesselInfo vessel) {
		VesselInfo exists = requireVessel(tenantId, vessel.getId());
		// 必须在归一化之前判断：归一化会把「未传」和「传了空串」都变成 null，
		// 之后就无法区分「这次只想停用、不动船名」和「把船名改成空白」。
		// 新增时校验船名、修改时不校验，是既有缺口 —— update 能把 NOT NULL 的
		// vessel_name 改成空串，随后列表与下拉全变空白。两条路径口径统一。
		if (vessel.getVesselName() != null && !StringUtils.hasText(vessel.getVesselName())) {
			throw new ArynBusinessException("船舶名称不能为空");
		}
		normalizeVessel(vessel);
		if (StringUtils.hasText(vessel.getImoCode()) && !Objects.equals(vessel.getImoCode(), exists.getImoCode())) {
			Long duplicated = vesselInfoMapper.selectCount(Wrappers.lambdaQuery(VesselInfo.class)
					.eq(VesselInfo::getTenantId, tenantId)
					.eq(VesselInfo::getImoCode, vessel.getImoCode())
					.ne(VesselInfo::getId, vessel.getId()));
			if (duplicated != null && duplicated > 0) {
				throw new ArynBusinessException("同一租户下 IMO 编号已存在");
			}
		}
		vessel.setTenantId(tenantId);
		vesselInfoMapper.updateById(vessel);
		return vessel;
	}

	/**
	 * 船舶档案文本字段归一化：去首尾空白，空白串统一转 null。
	 *
	 * <p>必须在写库前调用，原因是「空串 vs null」在两条写入路径上的行为不一致：
	 * <ul>
	 *   <li>{@code insert-strategy: not_empty} —— 新增时空串被省略成 NULL；</li>
	 *   <li>{@code update-strategy: not_null} —— 修改时 {@code ""} 会真的写进库。</li>
	 * </ul>
	 * 而 {@code vessel_info} 上有唯一键 {@code uk_vessel_info_imo(tenant_id, imo_code)}，
	 * MySQL 唯一索引允许多个 NULL、但不允许多个 {@code ""}。管理端修改弹窗提交的是
	 * 全量表单（未填的 IMO 是空串），于是「连续修改两条没有 IMO 的船」第二条必然撞键 1062。
	 * 这里把空白统一成 null，新增/修改两条路径就都回到「未填即 NULL」的同一语义。
	 *
	 * <p><b>已知限制</b>：update-strategy 为 not_null，所以传 null 不会清空已有值 ——
	 * 即「把已填的 IMO 改成空」目前做不到。这是既有行为，不是本次引入；要支持清空需改
	 * 字段级 updateStrategy，属于独立改动。
	 */
	private void normalizeVessel(VesselInfo vessel) {
		if (vessel == null) {
			return;
		}
		vessel.setVesselName(trimToNull(vessel.getVesselName()));
		vessel.setVesselNameEn(trimToNull(vessel.getVesselNameEn()));
		vessel.setImoCode(trimToNull(vessel.getImoCode()));
		vessel.setCallSign(trimToNull(vessel.getCallSign()));
		vessel.setFlagState(trimToNull(vessel.getFlagState()));
		vessel.setRemark(trimToNull(vessel.getRemark()));
		// 船型：空白回落缺省值；给了非法码值直接拒绝，避免把 DTO 注释里那套旧口径写进库
		String vesselType = trimToNull(vessel.getVesselType());
		if (vesselType == null) {
			vessel.setVesselType(VesselType.DEFAULT);
		}
		else if (!VesselType.isValid(vesselType)) {
			throw new ArynBusinessException("船舶类型不合法：" + vesselType);
		}
		else {
			vessel.setVesselType(vesselType);
		}
	}

	private String trimToNull(String value) {
		if (!StringUtils.hasText(value)) {
			return null;
		}
		return value.trim();
	}

	@Override
	public List<VesselMember> listMembers(String tenantId, String vesselId) {
		requireVessel(tenantId, vesselId);
		return vesselMemberMapper.selectList(Wrappers.lambdaQuery(VesselMember.class)
				.eq(VesselMember::getTenantId, tenantId)
				.eq(VesselMember::getVesselId, vesselId)
				.orderByDesc(VesselMember::getCreateTime));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VesselMember addMember(String tenantId, VesselMember member) {
		// 管理端直接绑定：来源固定 ADMIN_ADD。其他入口（分享链接自动补建等）必须带来源调下面的重载，
		// 否则会被错标成「管理端绑定」，来源标记就失去区分度。
		return addMember(tenantId, member, VesselMemberBindSource.ADMIN_ADD);
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VesselMember addMember(String tenantId, VesselMember member, String bindSource) {
		// 必须在营船才能加成员：此前只校验「船存在」，运营可以往停用船里塞人——
		// 停用船本来就无法下单，成员却仍能看到成员列表（昵称+脱敏手机号）与靠港计划，
		// 等于给了一条没有业务价值却泄露数据的通道。与 adminUnbindMember/requireVesselActive 同口径。
		VesselInfo vessel = requireVessel(tenantId, member.getVesselId());
		if (!VESSEL_STATUS_ACTIVE.equals(vessel.getStatus())) {
			throw new ArynBusinessException("船舶不存在或已停用");
		}
		if (!StringUtils.hasText(member.getUserId())) {
			throw new ArynBusinessException("成员用户ID不能为空");
		}
		Long duplicated = vesselMemberMapper.selectCount(Wrappers.lambdaQuery(VesselMember.class)
				.eq(VesselMember::getTenantId, tenantId)
				.eq(VesselMember::getVesselId, member.getVesselId())
				.eq(VesselMember::getUserId, member.getUserId()));
		if (duplicated != null && duplicated > 0) {
			throw new ArynBusinessException("该用户已是船舶成员");
		}
		String role = StringUtils.hasText(member.getMemberRole()) ? member.getMemberRole() : VesselMemberRole.CREW;
		// 角色白名单：本接口直接接原始实体，此前 memberRole 传什么就落什么，
		// 传 "1" 即可让被绑定人成为该船「发起人」。Registrar 内仍有一层兜底。
		if (!VesselMemberRole.isAssignable(role)) {
			throw new ArynBusinessException("成员角色不合法：" + role);
		}
		// 走统一入口：被移出过的用户重新绑定会复用历史行（唯一键不含 del_flag，直接 insert 必撞键 1062）
		memberRegistrar.ensureOnboard(tenantId, member.getVesselId(), member.getUserId(), role, "1",
				VesselMemberRole.CONFIRMER.equals(role) ? "1" : "0", member.getRemark(), bindSource);
		return vesselMemberMapper.selectOne(Wrappers.lambdaQuery(VesselMember.class)
				.eq(VesselMember::getTenantId, tenantId)
				.eq(VesselMember::getVesselId, member.getVesselId())
				.eq(VesselMember::getUserId, member.getUserId()));
	}

	@Override
	public List<VesselCall> listCalls(String tenantId, String vesselId) {
		requireVessel(tenantId, vesselId);
		return vesselCallMapper.selectList(Wrappers.lambdaQuery(VesselCall.class)
				.eq(VesselCall::getTenantId, tenantId)
				.eq(VesselCall::getVesselId, vesselId)
				.orderByAsc(VesselCall::getEta));
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VesselCall saveCall(String tenantId, VesselCall call) {
		requireVessel(tenantId, call.getVesselId());
		validateCallWindow(call);
		call.setId(null);
		call.setTenantId(tenantId);
		if (!StringUtils.hasText(call.getStatus())) {
			call.setStatus(CALL_STATUS_PLANNED);
		}
		vesselCallMapper.insert(call);
		reattachCartRows(tenantId, call);
		return call;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VesselCall declareCall(String tenantId, String userId, VesselCall call) {
		requireMembership(tenantId, call.getVesselId(), userId);
		validateCallWindow(call);

		// 去重：同一船舶已有未完成且 ETA 相近（±72 小时）的靠港计划时复用，
		// 避免同船多人对同一航次重复申报（业务确认窗口，2026-09-20）。
		LocalDateTime eta = call.getEta();
		if (eta != null) {
			VesselCall existing = vesselCallMapper.selectList(Wrappers.lambdaQuery(VesselCall.class)
					.eq(VesselCall::getTenantId, tenantId)
					.eq(VesselCall::getVesselId, call.getVesselId())
					.in(VesselCall::getStatus, List.of(CALL_STATUS_PLANNED, CALL_STATUS_BERTHED))
					.between(VesselCall::getEta, eta.minusHours(DECLARE_DEDUP_HOURS), eta.plusHours(DECLARE_DEDUP_HOURS))
					.last("LIMIT 1"))
				.stream().findFirst().orElse(null);
			if (existing != null) {
				log.info("船舶[{}]已存在相近靠港计划[{}]，复用而非新建申报", call.getVesselId(), existing.getId());
				// 复用的可能是上线前申报的靠港：借这次申报把滞留的无归属行顺延过去（迁移口径幂等）
				reattachCartRows(tenantId, existing);
				return existing;
			}
		}

		call.setId(null);
		call.setTenantId(tenantId);
		call.setSource(VesselCall.SOURCE_CREW);
		call.setDeclaredBy(userId);
		if (!StringUtils.hasText(call.getStatus())) {
			call.setStatus(CALL_STATUS_PLANNED);
		}
		// 配送时间窗属于公司排产决策，海员申报时不写入，由运营后续补充
		call.setDeliveryWindowStart(null);
		call.setDeliveryWindowEnd(null);
		vesselCallMapper.insert(call);
		log.info("海员[{}]申报靠港：vessel={} port={} eta={} etd={}", userId, call.getVesselId(),
				call.getPortCode(), call.getEta(), call.getEtd());
		reattachCartRows(tenantId, call);
		return call;
	}

	/**
	 * 新靠港生效后顺延购物车行归属：该船「无靠港归属」与「挂在已结束靠港」的行都迁到新靠港。
	 *
	 * <p>shopping_cart 在 order 库，跨模块走 Dubbo（接口定义在 order-api）。失败只记日志
	 * 不回滚靠港：申报是主业务，且迁移口径幂等——该船下一次申报时会自动补迁。
	 * 新靠港本身不可用（已完成/已取消/ETD 已过）时不迁移，避免把行挂上不可达的靠港。
	 */
	private void reattachCartRows(String tenantId, VesselCall call) {
		if (!isOrderableCall(call, LocalDateTime.now())) {
			return;
		}
		try {
			List<String> staleCallIds = vesselCallMapper.selectList(Wrappers.lambdaQuery(VesselCall.class)
					.eq(VesselCall::getTenantId, tenantId)
					.eq(VesselCall::getVesselId, call.getVesselId())
					.ne(VesselCall::getId, call.getId()))
				.stream()
				.filter(existing -> !isOrderableCall(existing, LocalDateTime.now()))
				.map(VesselCall::getId)
				.toList();
			int rows = remoteShoppingCartService.reattachRowsToVesselCall(tenantId, call.getVesselId(),
					call.getId(), staleCallIds);
			log.info("靠港[{}]生效：船舶[{}]购物车归属顺延 {} 行", call.getId(), call.getVesselId(), rows);
		}
		catch (Exception exception) {
			log.warn("靠港[{}]购物车归属迁移失败，待该船下次申报时自动补迁", call.getId(), exception);
		}
	}

	@Override
	public List<VesselCall> listDeclaredCalls(String tenantId) {
		return vesselCallMapper.selectList(Wrappers.lambdaQuery(VesselCall.class)
				.eq(VesselCall::getTenantId, tenantId)
				.eq(VesselCall::getSource, VesselCall.SOURCE_CREW)
				.in(VesselCall::getStatus, List.of(CALL_STATUS_PLANNED, CALL_STATUS_BERTHED))
				.orderByAsc(VesselCall::getEta));
	}

	@Override
	public int refreshCallStatus(String tenantId) {
		LocalDateTime now = LocalDateTime.now();
		// 计划中 → 靠泊中：已到 ETA 且未过 ETD（eta 为空的记录自然不命中）
		int toBerthed = vesselCallMapper.update(null, Wrappers.lambdaUpdate(VesselCall.class)
				.eq(VesselCall::getTenantId, tenantId)
				.eq(VesselCall::getStatus, CALL_STATUS_PLANNED)
				.le(VesselCall::getEta, now)
				.gt(VesselCall::getEtd, now)
				.set(VesselCall::getStatus, CALL_STATUS_BERTHED));
		// 计划中/靠泊中 → 已完成：已过 ETD（覆盖任务停摆导致跳过靠泊中的场景）
		int toCompleted = vesselCallMapper.update(null, Wrappers.lambdaUpdate(VesselCall.class)
				.eq(VesselCall::getTenantId, tenantId)
				.in(VesselCall::getStatus, List.of(CALL_STATUS_PLANNED, CALL_STATUS_BERTHED))
				.le(VesselCall::getEtd, now)
				.set(VesselCall::getStatus, CALL_STATUS_COMPLETED));
		if (toBerthed + toCompleted > 0) {
			log.info("租户[{}]靠港状态推进：靠泊中 {} 条，已完成 {} 条", tenantId, toBerthed, toCompleted);
		}
		return toBerthed + toCompleted;
	}

	@Override
	@Transactional(rollbackFor = Exception.class)
	public VesselCall updateCall(String tenantId, VesselCall call) {
		VesselCall exists = vesselCallMapper.selectOne(Wrappers.lambdaQuery(VesselCall.class)
				.eq(VesselCall::getTenantId, tenantId)
				.eq(VesselCall::getId, call.getId()));
		if (exists == null) {
			throw new ArynBusinessException("靠港计划不存在");
		}
		if (!CALL_STATUS_PLANNED.equals(exists.getStatus())) {
			throw new ArynBusinessException("仅计划中的靠港允许修改");
		}
		validateCallWindow(call.getEta() != null ? call : exists,
				call.getEtd() != null ? call : exists,
				call.getDeliveryWindowStart() != null || call.getDeliveryWindowEnd() != null ? call : exists);
		call.setTenantId(tenantId);
		vesselCallMapper.updateById(call);
		recordChangeAndNotify(tenantId, exists, call, call.getOperatorId(), call.getOperatorName());
		return call;
	}

	/**
	 * 靠港计划变更留痕与事件发布：ETA/ETD/泊位/时间窗任一变化才触发；
	 * 通知失败仅告警，不阻断变更主流程（fail-open）。
	 * 快照原则：已支付订单的配送上下文快照不自动改写，由订单域提醒相关用户确认。
	 */
	void recordChangeAndNotify(String tenantId, VesselCall before, VesselCall after, String operatorId,
			String operatorName) {
		LocalDateTime oldEta = before.getEta();
		LocalDateTime newEta = after.getEta() != null ? after.getEta() : before.getEta();
		LocalDateTime oldEtd = before.getEtd();
		LocalDateTime newEtd = after.getEtd() != null ? after.getEtd() : before.getEtd();
		String oldBerth = before.getBerth();
		String newBerth = after.getBerth() != null ? after.getBerth() : before.getBerth();
		LocalDateTime oldStart = before.getDeliveryWindowStart();
		LocalDateTime newStart = after.getDeliveryWindowStart() != null ? after.getDeliveryWindowStart()
				: before.getDeliveryWindowStart();
		LocalDateTime oldEnd = before.getDeliveryWindowEnd();
		LocalDateTime newEnd = after.getDeliveryWindowEnd() != null ? after.getDeliveryWindowEnd()
				: before.getDeliveryWindowEnd();

		boolean etaChanged = !java.util.Objects.equals(oldEta, newEta);
		boolean etdChanged = !java.util.Objects.equals(oldEtd, newEtd);
		boolean berthChanged = !java.util.Objects.equals(oldBerth, newBerth);
		boolean windowChanged = !java.util.Objects.equals(oldStart, newStart)
				|| !java.util.Objects.equals(oldEnd, newEnd);
		if (!etaChanged && !etdChanged && !berthChanged && !windowChanged) {
			return;
		}

		com.aryn.cloud.vessel.api.entity.VesselCallChangeLog changeLog = new com.aryn.cloud.vessel.api.entity.VesselCallChangeLog();
		changeLog.setId(com.baomidou.mybatisplus.core.toolkit.IdWorker.getIdStr());
		changeLog.setCallId(before.getId());
		changeLog.setVesselId(before.getVesselId());
		changeLog.setOldEta(oldEta);
		changeLog.setNewEta(newEta);
		changeLog.setOldEtd(oldEtd);
		changeLog.setNewEtd(newEtd);
		changeLog.setOldBerth(oldBerth);
		changeLog.setNewBerth(newBerth);
		changeLog.setOldWindowStart(oldStart);
		changeLog.setNewWindowStart(newStart);
		changeLog.setOldWindowEnd(oldEnd);
		changeLog.setNewWindowEnd(newEnd);
		changeLog.setOperatorId(operatorId);
		changeLog.setOperatorName(operatorName);
		changeLog.setTenantId(tenantId);
		changeLog.setCreateTime(LocalDateTime.now());
		changeLog.setDelFlag("0");
		vesselCallChangeLogMapper.insert(changeLog);

		try {
			com.aryn.cloud.vessel.api.dto.VesselCallChangedNotice notice = new com.aryn.cloud.vessel.api.dto.VesselCallChangedNotice();
			notice.setChangeLogId(changeLog.getId());
			notice.setTenantId(tenantId);
			notice.setCallId(before.getId());
			notice.setVesselId(before.getVesselId());
			notice.setPortCode(before.getPortCode());
			notice.setPortName(before.getPortName());
			notice.setOldEta(oldEta);
			notice.setNewEta(newEta);
			notice.setOldEtd(oldEtd);
			notice.setNewEtd(newEtd);
			notice.setOldBerth(oldBerth);
			notice.setNewBerth(newBerth);
			notice.setOldWindowStart(oldStart);
			notice.setNewWindowStart(newStart);
			notice.setOldWindowEnd(oldEnd);
			notice.setNewWindowEnd(newEnd);
			notice.setOperatorName(operatorName);
			rocketMQTemplate.convertAndSend(
					com.aryn.cloud.common.core.constant.RocketMqConstants.VESSEL_CALL_CHANGED_TOPIC, notice);
		}
		catch (Exception ex) {
			log.error("靠港计划[{}]变更事件发布失败，仅保留变更日志", before.getId(), ex);
		}
	}

	@Override
	public List<VesselInfo> myVessels(String tenantId, String userId) {
		List<VesselMember> memberships = vesselMemberMapper.selectList(Wrappers.lambdaQuery(VesselMember.class)
				.eq(VesselMember::getTenantId, tenantId)
				.eq(VesselMember::getUserId, userId)
				.eq(VesselMember::getStatus, MEMBER_STATUS_ONBOARD));
		if (memberships.isEmpty()) {
			return List.of();
		}
		List<String> vesselIds = memberships.stream().map(VesselMember::getVesselId).distinct().toList();
		return vesselInfoMapper.selectList(Wrappers.lambdaQuery(VesselInfo.class)
				.eq(VesselInfo::getTenantId, tenantId)
				.in(VesselInfo::getId, vesselIds)
				.eq(VesselInfo::getStatus, VESSEL_STATUS_ACTIVE)
				.orderByDesc(VesselInfo::getCreateTime));
	}

	@Override
	public List<VesselCall> availableCalls(String tenantId, String userId, String vesselId) {
		requireMembership(tenantId, vesselId, userId);
		LocalDateTime now = LocalDateTime.now();
		return vesselCallMapper.selectList(Wrappers.lambdaQuery(VesselCall.class)
				.eq(VesselCall::getTenantId, tenantId)
				.eq(VesselCall::getVesselId, vesselId)
				.in(VesselCall::getStatus, List.of(CALL_STATUS_PLANNED, CALL_STATUS_BERTHED))
				.gt(VesselCall::getEtd, now)
				.orderByAsc(VesselCall::getEta))
				.stream()
				.filter(call -> isOrderableCall(call, now))
				.toList();
	}

	/**
	 * 判断靠港计划能否作为新订单的配送计划：未完成/未取消且尚未离港。
	 */
	public static boolean isOrderableCall(VesselCall call, LocalDateTime now) {
		if (call == null || call.getEtd() == null) {
			return false;
		}
		if (CALL_STATUS_COMPLETED.equals(call.getStatus()) || CALL_STATUS_CANCELED.equals(call.getStatus())) {
			return false;
		}
		return call.getEtd().isAfter(now);
	}

	@Override
	public VesselContextDTO currentContext(String tenantId, String userId, String vesselId) {
		List<VesselCall> calls = availableCalls(tenantId, userId, vesselId);
		if (calls.isEmpty()) {
			return null;
		}
		return toContext(calls.get(0), requireVessel(tenantId, vesselId));
	}

	@Override
	public boolean isVesselMember(String tenantId, String vesselId, String userId) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(vesselId) || !StringUtils.hasText(userId)) {
			return false;
		}
		Long count = vesselMemberMapper.selectCount(Wrappers.lambdaQuery(VesselMember.class)
				.eq(VesselMember::getTenantId, tenantId)
				.eq(VesselMember::getVesselId, vesselId)
				.eq(VesselMember::getUserId, userId)
				.eq(VesselMember::getStatus, MEMBER_STATUS_ONBOARD));
		return count != null && count > 0;
	}

	@Override
	public String memberRole(String tenantId, String vesselId, String userId) {
		if (!StringUtils.hasText(tenantId) || !StringUtils.hasText(vesselId) || !StringUtils.hasText(userId)) {
			return null;
		}
		VesselMember member = vesselMemberMapper.selectOne(Wrappers.lambdaQuery(VesselMember.class)
				.eq(VesselMember::getTenantId, tenantId)
				.eq(VesselMember::getVesselId, vesselId)
				.eq(VesselMember::getUserId, userId)
				.eq(VesselMember::getStatus, MEMBER_STATUS_ONBOARD)
				.last("LIMIT 1"));
		return member != null ? member.getMemberRole() : null;
	}

	@Override
	public VesselContextDTO contextByCallId(String tenantId, String vesselCallId) {
		if (!StringUtils.hasText(vesselCallId)) {
			return null;
		}
		VesselCall call = vesselCallMapper.selectOne(Wrappers.lambdaQuery(VesselCall.class)
				.eq(VesselCall::getTenantId, tenantId)
				.eq(VesselCall::getId, vesselCallId));
		if (!isOrderableCall(call, LocalDateTime.now())) {
			return null;
		}
		return toContext(call, vesselInfoMapper.selectOne(Wrappers.lambdaQuery(VesselInfo.class)
				.eq(VesselInfo::getTenantId, tenantId)
				.eq(VesselInfo::getId, call.getVesselId())));
	}

	@Override
	public VesselContextDTO snapshotByCallId(String tenantId, String vesselCallId) {
		if (!StringUtils.hasText(vesselCallId)) {
			return null;
		}
		// 不做可下单过滤：历史单据要显示当时的船名与港口，靠港结束不是「查不到」的理由。
		// 可下单性由 callOrderable 标记带给调用方（详情页据此提示提交时会顺延）。
		VesselCall call = vesselCallMapper.selectOne(Wrappers.lambdaQuery(VesselCall.class)
				.eq(VesselCall::getTenantId, tenantId)
				.eq(VesselCall::getId, vesselCallId));
		if (call == null) {
			return null;
		}
		return toContext(call, vesselInfoMapper.selectOne(Wrappers.lambdaQuery(VesselInfo.class)
				.eq(VesselInfo::getTenantId, tenantId)
				.eq(VesselInfo::getId, call.getVesselId())));
	}

	@Override
	public VesselContextDTO resolveAvailableCall(String tenantId, String vesselId) {
		if (!StringUtils.hasText(vesselId)) {
			return null;
		}
		LocalDateTime now = LocalDateTime.now();
		return vesselCallMapper.selectList(Wrappers.lambdaQuery(VesselCall.class)
				.eq(VesselCall::getTenantId, tenantId)
				.eq(VesselCall::getVesselId, vesselId)
				.in(VesselCall::getStatus, List.of(CALL_STATUS_PLANNED, CALL_STATUS_BERTHED))
				.gt(VesselCall::getEtd, now)
				.orderByAsc(VesselCall::getEta))
			.stream()
			.filter(call -> isOrderableCall(call, now))
			.findFirst()
			.map(call -> toContext(call, vesselInfoMapper.selectOne(Wrappers.lambdaQuery(VesselInfo.class)
					.eq(VesselInfo::getTenantId, tenantId)
					.eq(VesselInfo::getId, call.getVesselId()))))
			.orElse(null);
	}

	private VesselInfo requireVessel(String tenantId, String vesselId) {
		if (!StringUtils.hasText(vesselId)) {
			throw new ArynBusinessException("船舶ID不能为空");
		}
		VesselInfo vessel = vesselInfoMapper.selectOne(Wrappers.lambdaQuery(VesselInfo.class)
				.eq(VesselInfo::getTenantId, tenantId)
				.eq(VesselInfo::getId, vesselId));
		if (vessel == null) {
			throw new ArynBusinessException("船舶不存在");
		}
		return vessel;
	}

	private void requireMembership(String tenantId, String vesselId, String userId) {
		Long count = vesselMemberMapper.selectCount(Wrappers.lambdaQuery(VesselMember.class)
				.eq(VesselMember::getTenantId, tenantId)
				.eq(VesselMember::getVesselId, vesselId)
				.eq(VesselMember::getUserId, userId)
				.eq(VesselMember::getStatus, MEMBER_STATUS_ONBOARD));
		if (count == null || count == 0) {
			throw new ArynBusinessException("无权访问该船舶上下文");
		}
	}

	private void validateCallWindow(VesselCall call) {
		validateCallWindow(call, call, call);
	}

	/**
	 * 校验靠港时间合法性：ETA 必须早于 ETD，配送时间窗必须落在 [ETA, ETD] 内且先后合法。
	 */
	private void validateCallWindow(VesselCall etaSource, VesselCall etdSource, VesselCall windowSource) {
		LocalDateTime eta = etaSource.getEta();
		LocalDateTime etd = etdSource.getEtd();
		if (eta == null || etd == null) {
			throw new ArynBusinessException("靠港计划必须包含 ETA 和 ETD");
		}
		if (!eta.isBefore(etd)) {
			throw new ArynBusinessException("ETA 必须早于 ETD");
		}
		LocalDateTime windowStart = windowSource.getDeliveryWindowStart();
		LocalDateTime windowEnd = windowSource.getDeliveryWindowEnd();
		if ((windowStart == null) != (windowEnd == null)) {
			throw new ArynBusinessException("配送时间窗必须同时提供开始和结束时间");
		}
		if (windowStart != null) {
			if (windowStart.isBefore(eta) || windowEnd.isAfter(etd)) {
				throw new ArynBusinessException("配送时间窗必须落在 ETA 与 ETD 之间");
			}
			if (!windowStart.isBefore(windowEnd)) {
				throw new ArynBusinessException("配送时间窗开始必须早于结束");
			}
		}
	}

	private VesselContextDTO toContext(VesselCall call, VesselInfo vessel) {
		VesselContextDTO context = new VesselContextDTO();
		context.setVesselId(call.getVesselId());
		context.setVesselName(vessel != null ? vessel.getVesselName() : null);
		context.setVesselCallId(call.getId());
		context.setPortCode(call.getPortCode());
		context.setPortName(call.getPortName());
		context.setBerth(call.getBerth());
		context.setEta(call.getEta());
		context.setEtd(call.getEtd());
		context.setDeliveryWindowStart(call.getDeliveryWindowStart());
		context.setDeliveryWindowEnd(call.getDeliveryWindowEnd());
		// 统一在此计算，快照查询才能把「已失效但仍有名称」的靠港带回去
		context.setCallOrderable(isOrderableCall(call, LocalDateTime.now()));
		return context;
	}


	@Override
	public List<com.aryn.cloud.vessel.api.vo.VesselCallCalendarVO> calendar(String tenantId, LocalDateTime start,
			LocalDateTime end) {
		if (start == null || end == null || !start.isBefore(end)) {
			throw new ArynBusinessException("日历时间区间不合法");
		}
		List<VesselCall> calls = vesselCallMapper.selectList(Wrappers.lambdaQuery(VesselCall.class)
				.eq(VesselCall::getTenantId, tenantId)
				.le(VesselCall::getEta, end)
				.ge(VesselCall::getEtd, start)
				.ne(VesselCall::getStatus, "4")
				.orderByAsc(VesselCall::getEta));
		if (calls.isEmpty()) {
			return List.of();
		}
		List<String> vesselIds = calls.stream().map(VesselCall::getVesselId).distinct().toList();
		java.util.Map<String, String> vesselNames = vesselInfoMapper.selectList(
				Wrappers.lambdaQuery(VesselInfo.class)
						.eq(VesselInfo::getTenantId, tenantId)
						.in(VesselInfo::getId, vesselIds))
				.stream()
				.collect(java.util.stream.Collectors.toMap(VesselInfo::getId, VesselInfo::getVesselName,
						(a, b) -> a));
		return calls.stream().map(call -> {
			com.aryn.cloud.vessel.api.vo.VesselCallCalendarVO vo = new com.aryn.cloud.vessel.api.vo.VesselCallCalendarVO();
			vo.setCallId(call.getId());
			vo.setVesselId(call.getVesselId());
			vo.setVesselName(vesselNames.get(call.getVesselId()));
			vo.setPortCode(call.getPortCode());
			vo.setPortName(call.getPortName());
			vo.setBerth(call.getBerth());
			vo.setEta(call.getEta());
			vo.setEtd(call.getEtd());
			vo.setDeliveryWindowStart(call.getDeliveryWindowStart());
			vo.setDeliveryWindowEnd(call.getDeliveryWindowEnd());
			vo.setStatus(call.getStatus());
			return vo;
		}).toList();
	}

}
