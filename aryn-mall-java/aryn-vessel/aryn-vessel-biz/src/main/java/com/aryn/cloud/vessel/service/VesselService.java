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
	 * 海员申报靠港：校验船舶成员关系后落库，来源标记为「海员申报」。
	 *
	 * <p>公司无法与船舶公司对接船期，ETA/ETD 只有船上的人知道；
	 * 因此港口/泊位/到离港时间由海员在下单时申报，运营收到后再排产
	 * （填配送时间窗、排波次、派车）。同一船舶已有未完成且 ETA 相近
	 * （±72 小时）的靠港时复用，避免同一航次被多人重复申报。
	 *
	 * @return 新建或被复用的靠港计划
	 */
	VesselCall declareCall(String tenantId, String userId, VesselCall call);

	/**
	 * 待处理的靠港申报（运营视角：来源为海员申报且尚未排产）
	 */
	List<VesselCall> listDeclaredCalls(String tenantId);

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
