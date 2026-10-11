package com.aryn.cloud.vessel.service.impl;

import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.vessel.api.entity.VesselCall;
import com.aryn.cloud.vessel.api.entity.VesselPortVehicle;
import com.aryn.cloud.vessel.mapper.VesselCallMapper;
import com.aryn.cloud.vessel.mapper.VesselPortVehicleMapper;
import com.aryn.cloud.vessel.service.VesselPortVehicleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 港口报备车辆服务实现。
 *
 * @author aryn
 * @since 2026/10/11
 */
@Service
@RequiredArgsConstructor
public class VesselPortVehicleServiceImpl implements VesselPortVehicleService {

	private final VesselPortVehicleMapper portVehicleMapper;

	private final VesselCallMapper vesselCallMapper;

	@Override
	public IPage<VesselPortVehicle> pageVehicles(String tenantId, IPage<VesselPortVehicle> page,
			String portName, String plateNo, String driverName, String status) {
		LambdaQueryWrapper<VesselPortVehicle> wrapper = Wrappers.lambdaQuery(VesselPortVehicle.class)
				.eq(VesselPortVehicle::getTenantId, tenantId)
				.eq(StringUtils.hasText(portName), VesselPortVehicle::getPortName, portName)
				.like(StringUtils.hasText(plateNo), VesselPortVehicle::getPlateNo, plateNo)
				.like(StringUtils.hasText(driverName), VesselPortVehicle::getDriverName, driverName)
				.eq(StringUtils.hasText(status), VesselPortVehicle::getStatus, status)
				.orderByAsc(VesselPortVehicle::getSort)
				.orderByDesc(VesselPortVehicle::getCreateTime);
		return portVehicleMapper.selectPage(page, wrapper);
	}

	@Override
	public List<String> listPortOptions(String tenantId) {
		Set<String> ports = new LinkedHashSet<>();
		portVehicleMapper.selectList(Wrappers.lambdaQuery(VesselPortVehicle.class)
						.eq(VesselPortVehicle::getTenantId, tenantId)
						.select(VesselPortVehicle::getPortName))
				.stream()
				.map(VesselPortVehicle::getPortName)
				.filter(StringUtils::hasText)
				.forEach(ports::add);
		// 靠港计划里出现过的港口也进建议，避免录入时手打港名与既有口径对不上
		vesselCallMapper.selectList(Wrappers.lambdaQuery(VesselCall.class)
						.eq(VesselCall::getTenantId, tenantId)
						.select(VesselCall::getPortName))
				.stream()
				.map(VesselCall::getPortName)
				.filter(StringUtils::hasText)
				.forEach(ports::add);
		return List.copyOf(ports);
	}

	@Override
	public VesselPortVehicle saveVehicle(String tenantId, VesselPortVehicle vehicle) {
		validateVehicle(vehicle);
		String portName = vehicle.getPortName().trim();
		String plateNo = vehicle.getPlateNo().trim();
		assertPlateFree(tenantId, portName, plateNo, null);
		vehicle.setId(null);
		vehicle.setTenantId(tenantId);
		vehicle.setPortName(portName);
		vehicle.setPlateNo(plateNo);
		if (vehicle.getDriverName() != null) {
			vehicle.setDriverName(vehicle.getDriverName().trim());
		}
		if (!StringUtils.hasText(vehicle.getStatus())) {
			vehicle.setStatus(VesselPortVehicle.STATUS_ENABLED);
		}
		if (vehicle.getSort() == null) {
			vehicle.setSort(0);
		}
		portVehicleMapper.insert(vehicle);
		return vehicle;
	}

	@Override
	public VesselPortVehicle updateVehicle(String tenantId, VesselPortVehicle vehicle) {
		if (!StringUtils.hasText(vehicle.getId())) {
			throw new ArynBusinessException("缺少车辆记录ID");
		}
		VesselPortVehicle exist = requireVehicle(tenantId, vehicle.getId());
		validateVehicle(vehicle);
		String portName = vehicle.getPortName().trim();
		String plateNo = vehicle.getPlateNo().trim();
		assertPlateFree(tenantId, portName, plateNo, vehicle.getId());
		exist.setPortName(portName);
		exist.setPlateNo(plateNo);
		exist.setDriverName(vehicle.getDriverName());
		exist.setDriverPhone(vehicle.getDriverPhone().trim());
		exist.setSeatCount(vehicle.getSeatCount());
		exist.setVehicleDesc(vehicle.getVehicleDesc());
		exist.setPickupPoint(vehicle.getPickupPoint());
		exist.setRemark(vehicle.getRemark());
		exist.setSort(vehicle.getSort() == null ? 0 : vehicle.getSort());
		exist.setStatus(vehicle.getStatus());
		portVehicleMapper.updateById(exist);
		return exist;
	}

	@Override
	public void deleteVehicle(String tenantId, String id) {
		requireVehicle(tenantId, id);
		portVehicleMapper.deleteById(id);
	}

	@Override
	public List<VesselPortVehicle> listEnabledByPort(String tenantId, String portName) {
		if (!StringUtils.hasText(portName)) {
			return List.of();
		}
		return portVehicleMapper.selectList(Wrappers.lambdaQuery(VesselPortVehicle.class)
				.eq(VesselPortVehicle::getTenantId, tenantId)
				.eq(VesselPortVehicle::getPortName, portName.trim())
				.eq(VesselPortVehicle::getStatus, VesselPortVehicle.STATUS_ENABLED)
				.orderByAsc(VesselPortVehicle::getSort)
				.orderByAsc(VesselPortVehicle::getCreateTime));
	}

	@Override
	public List<String> listEnabledPorts(String tenantId) {
		return portVehicleMapper.selectList(Wrappers.lambdaQuery(VesselPortVehicle.class)
						.eq(VesselPortVehicle::getTenantId, tenantId)
						.eq(VesselPortVehicle::getStatus, VesselPortVehicle.STATUS_ENABLED)
						.select(VesselPortVehicle::getPortName))
				.stream()
				.map(VesselPortVehicle::getPortName)
				.filter(StringUtils::hasText)
				.distinct()
				.toList();
	}

	private VesselPortVehicle requireVehicle(String tenantId, String id) {
		VesselPortVehicle vehicle = portVehicleMapper.selectById(id);
		if (vehicle == null || !tenantId.equals(vehicle.getTenantId())) {
			throw new ArynBusinessException("报备车辆不存在");
		}
		return vehicle;
	}

	private void validateVehicle(VesselPortVehicle vehicle) {
		if (!StringUtils.hasText(vehicle.getPortName())) {
			throw new ArynBusinessException("港口名称不能为空");
		}
		if (!StringUtils.hasText(vehicle.getPlateNo())) {
			throw new ArynBusinessException("车牌号不能为空");
		}
		if (!StringUtils.hasText(vehicle.getDriverPhone())) {
			throw new ArynBusinessException("司机联系电话不能为空");
		}
	}

	private void assertPlateFree(String tenantId, String portName, String plateNo, String excludeId) {
		Long duplicated = portVehicleMapper.selectCount(Wrappers.lambdaQuery(VesselPortVehicle.class)
				.eq(VesselPortVehicle::getTenantId, tenantId)
				.eq(VesselPortVehicle::getPortName, portName)
				.eq(VesselPortVehicle::getPlateNo, plateNo)
				.ne(StringUtils.hasText(excludeId), VesselPortVehicle::getId, excludeId));
		if (duplicated != null && duplicated > 0) {
			throw new ArynBusinessException("该港口已登记此车牌，可直接编辑原记录");
		}
	}

}
