package com.aryn.cloud.vessel.service;

import com.aryn.cloud.vessel.api.entity.VesselPortVehicle;
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.List;

/**
 * 港口报备车辆服务。
 *
 * @author aryn
 * @since 2026/10/11
 */
public interface VesselPortVehicleService {

	/** 管理端分页（港口/车牌/司机/状态筛选） */
	IPage<VesselPortVehicle> pageVehicles(String tenantId, IPage<VesselPortVehicle> page,
			String portName, String plateNo, String driverName, String status);

	/** 港口名建议选项：已登记车辆港口 ∪ 靠港计划港口（去重） */
	List<String> listPortOptions(String tenantId);

	/** 新增报备车辆 */
	VesselPortVehicle saveVehicle(String tenantId, VesselPortVehicle vehicle);

	/** 修改报备车辆 */
	VesselPortVehicle updateVehicle(String tenantId, VesselPortVehicle vehicle);

	/** 删除报备车辆（逻辑删除） */
	void deleteVehicle(String tenantId, String id);

	/** C 端：某港口启用中的报备车辆（sort 升序） */
	List<VesselPortVehicle> listEnabledByPort(String tenantId, String portName);

	/** C 端：已登记车辆的港口清单（启用中） */
	List<String> listEnabledPorts(String tenantId);

}
