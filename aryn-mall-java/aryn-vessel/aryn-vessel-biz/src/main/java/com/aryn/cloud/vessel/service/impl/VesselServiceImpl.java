package com.aryn.cloud.vessel.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
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

	private final VesselInfoMapper vesselInfoMapper;

	private final VesselMemberMapper vesselMemberMapper;

	private final VesselCallMapper vesselCallMapper;

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
		requireVessel(tenantId, member.getVesselId());
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
		member.setId(null);
		member.setTenantId(tenantId);
		if (!StringUtils.hasText(member.getStatus())) {
			member.setStatus(MEMBER_STATUS_ONBOARD);
		}
		if (!StringUtils.hasText(member.getMemberRole())) {
			member.setMemberRole("2");
		}
		member.setJoinTime(LocalDateTime.now());
		vesselMemberMapper.insert(member);
		return member;
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
		return call;
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
		return call;
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
