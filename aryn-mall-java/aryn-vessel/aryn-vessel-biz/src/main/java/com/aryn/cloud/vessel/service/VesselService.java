package com.aryn.cloud.vessel.service;

import com.aryn.cloud.vessel.api.dto.VesselContextDTO;
import com.aryn.cloud.vessel.api.entity.VesselCall;
import com.aryn.cloud.vessel.api.entity.VesselInfo;
import com.aryn.cloud.vessel.api.entity.VesselMember;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 船舶与靠港计划服务。
 *
 * @author aryn
 * @since 2026/9/11
 */
public interface VesselService {

	/**
	 * 管理端船舶分页。
	 */
	IPage<VesselInfo> pageVessels(String tenantId, IPage<VesselInfo> page, VesselInfo query);

	/**
	 * 管理端新增船舶。
	 */
	VesselInfo saveVessel(String tenantId, VesselInfo vessel);

	/**
	 * 管理端修改船舶（含停用）。
	 */
	VesselInfo updateVessel(String tenantId, VesselInfo vessel);

	/**
	 * 管理端查看船舶成员。
	 */
	List<VesselMember> listMembers(String tenantId, String vesselId);

	/**
	 * 管理端绑定船舶成员（后台邀请/授权）。
	 */
	VesselMember addMember(String tenantId, VesselMember member);

	/**
	 * 管理端查看靠港计划。
	 */
	List<VesselCall> listCalls(String tenantId, String vesselId);

	/**
	 * 管理端新增靠港计划。
	 */
	VesselCall saveCall(String tenantId, VesselCall call);

	/**
	 * 管理端修改靠港计划（ETA/泊位/时间窗等）。
	 */
	VesselCall updateCall(String tenantId, VesselCall call);

	/**
	 * C 端：当前用户作为在船成员的船舶列表。
	 */
	List<VesselInfo> myVessels(String tenantId, String userId);

	/**
	 * C 端：指定船舶可用的靠港计划（排除已过期/已完成/已取消）。
	 */
	List<VesselCall> availableCalls(String tenantId, String userId, String vesselId);

	/**
	 * C 端：当前船舶上下文（下一靠港与配送时间窗）。
	 */
	VesselContextDTO currentContext(String tenantId, String userId, String vesselId);

	/**
	 * 判断用户是否为指定船舶的在船成员（供远程调用校验，不做额外权限门控）。
	 */
	boolean isVesselMember(String tenantId, String vesselId, String userId);

	/**
	 * 按靠港计划 ID 查询可配送上下文（船舶+港口+时间窗）；不存在或已过期返回 null。
	 */
	VesselContextDTO contextByCallId(String tenantId, String vesselCallId);

	/**
	 * 靠港日历：查询时间区间内（ETA/ETD 与区间有交集）的全部靠港计划，附船舶名称。
	 */
	List<com.aryn.cloud.vessel.api.vo.VesselCallCalendarVO> calendar(String tenantId,
			java.time.LocalDateTime start, java.time.LocalDateTime end);

}
