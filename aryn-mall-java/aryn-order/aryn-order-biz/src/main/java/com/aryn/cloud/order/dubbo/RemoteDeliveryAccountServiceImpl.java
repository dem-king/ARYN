
package com.aryn.cloud.order.dubbo;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.aryn.cloud.order.api.dto.DeliveryEligibilityDTO;
import com.aryn.cloud.order.api.entity.DeliveryAccountBinding;
import com.aryn.cloud.order.api.entity.DeliveryStaff;
import com.aryn.cloud.order.api.entity.DeliveryTask;
import com.aryn.cloud.order.api.remote.RemoteDeliveryAccountService;
import com.aryn.cloud.order.mapper.DeliveryTaskMapper;
import com.aryn.cloud.order.service.IDeliveryAccountBindingService;
import com.aryn.cloud.order.service.IDeliveryStaffService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 配送账号绑定远程服务实现
 *
 * <p>对绑定与配送员资料做一致性校验：资料被删除或 staff.userId 与绑定归属
 * 不一致时返回 STAFF_INVALID，由认证服务拒绝资格与身份换取。
 *
 * @author aryn
 * @since 2026/9/5
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteDeliveryAccountServiceImpl implements RemoteDeliveryAccountService {

	/** 待处理任务状态：待取货/配货中/待送达 */
	private static final List<String> PENDING_TASK_STATUSES = List.of("2", "3", "4");

	private final IDeliveryAccountBindingService deliveryAccountBindingService;

	private final IDeliveryStaffService deliveryStaffService;

	private final DeliveryTaskMapper deliveryTaskMapper;

	@Override
	public DeliveryEligibilityDTO getEligibilityByMallUser(String mallUserId) {
		if (StrUtil.isBlank(mallUserId)) {
			return DeliveryEligibilityDTO.unbound();
		}
		DeliveryAccountBinding binding = deliveryAccountBindingService.getActiveByMallUser(mallUserId);
		if (binding == null) {
			return DeliveryEligibilityDTO.unbound();
		}
		DeliveryEligibilityDTO dto = new DeliveryEligibilityDTO();
		dto.setTenantId(binding.getTenantId());
		dto.setSysUserId(binding.getSysUserId());
		dto.setDeliveryStaffId(binding.getDeliveryStaffId());
		DeliveryStaff staff = deliveryStaffService.getById(binding.getDeliveryStaffId());
		boolean staffConsistent = staff != null && Objects.equals(staff.getUserId(), binding.getSysUserId());
		if (!staffConsistent) {
			dto.setBindingStatus(DeliveryEligibilityDTO.BINDING_STAFF_INVALID);
			return dto;
		}
		dto.setBindingStatus(DeliveryEligibilityDTO.BINDING_BOUND);
		dto.setStaffName(staff.getStaffName());
		dto.setStaffStatus(staff.getStatus());
		dto.setPendingTaskCount(countPendingTasks(binding.getDeliveryStaffId()));
		return dto;
	}

	private int countPendingTasks(String staffId) {
		if (StrUtil.isBlank(staffId)) {
			return 0;
		}
		List<Map<String, Object>> rows = deliveryTaskMapper.selectMaps(new QueryWrapper<DeliveryTask>()
			.select("count(*) as cnt")
			.eq("staff_id", staffId)
			.in("status", PENDING_TASK_STATUSES));
		if (rows.isEmpty() || rows.get(0).get("cnt") == null) {
			return 0;
		}
		return Integer.parseInt(String.valueOf(rows.get(0).get("cnt")));
	}

}
