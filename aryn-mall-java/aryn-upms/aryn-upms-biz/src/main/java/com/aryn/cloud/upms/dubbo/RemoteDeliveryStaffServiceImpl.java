package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.DeliveryStaffQuery;
import com.aryn.cloud.upms.api.remote.RemoteDeliveryStaffService;
import com.aryn.cloud.upms.api.vo.DeliveryStaffPageVO;
import com.aryn.cloud.upms.api.vo.DeliveryStaffVO;
import com.aryn.cloud.upms.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 商城配送员候选与资格查询实现。 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteDeliveryStaffServiceImpl implements RemoteDeliveryStaffService {

	private static final int DEFAULT_LIMIT = 500;

	private static final int MAX_LIMIT = 1000;

	private final SysUserMapper sysUserMapper;

	@Override
	public DeliveryStaffPageVO queryCandidates(DeliveryStaffQuery query) {
		requireTenant(query == null ? null : query.getTenantId());
		int limit = normalizeLimit(query.getLimit());
		List<DeliveryStaffVO> queried = sysUserMapper.selectDeliveryStaff(query, limit + 1);
		List<DeliveryStaffVO> records = queried == null ? new ArrayList<>() : new ArrayList<>(queried);
		boolean hasMore = records.size() > limit;
		if (hasMore) {
			records.remove(records.size() - 1);
		}
		DeliveryStaffPageVO result = new DeliveryStaffPageVO();
		result.setRecords(records);
		result.setHasMore(hasMore);
		result.setNextCursor(records.isEmpty() ? null : records.get(records.size() - 1).getId());
		return result;
	}

	@Override
	public boolean isEligible(String tenantId, String staffId) {
		requireTenant(tenantId);
		if (staffId == null || staffId.isBlank()) {
			return false;
		}
		return sysUserMapper.countEligibleDeliveryStaff(tenantId, staffId) > 0;
	}

	private void requireTenant(String tenantId) {
		String currentTenantId = ArynTenantContextHolder.getTenantId();
		if (tenantId == null || tenantId.isBlank() || !Objects.equals(currentTenantId, tenantId)) {
			throw new ArynBusinessException("商城配送员查询租户不匹配");
		}
	}

	private int normalizeLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
	}

}
