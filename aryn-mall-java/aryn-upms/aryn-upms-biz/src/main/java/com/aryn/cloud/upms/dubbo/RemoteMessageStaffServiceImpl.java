package com.aryn.cloud.upms.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.upms.api.dto.StaffMessageAudienceRequest;
import com.aryn.cloud.upms.api.remote.RemoteMessageStaffService;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;
import com.aryn.cloud.upms.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 消息域工作人员受众与客服资格查询实现。 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteMessageStaffServiceImpl implements RemoteMessageStaffService {

	private static final int DEFAULT_LIMIT = 500;

	private static final int MAX_LIMIT = 1000;

	private final SysUserMapper sysUserMapper;

	@Override
	public StaffMessageAudiencePageVO queryRecipients(StaffMessageAudienceRequest request) {
		requireTenant(request == null ? null : request.getTenantId());
		int limit = normalizeLimit(request.getLimit());
		List<StaffMessageRecipientVO> queried = sysUserMapper.selectMessageRecipients(request, limit + 1);
		List<StaffMessageRecipientVO> records = queried == null ? new ArrayList<>() : new ArrayList<>(queried);
		boolean hasMore = records.size() > limit;
		if (hasMore) {
			records.remove(records.size() - 1);
		}
		StaffMessageAudiencePageVO result = new StaffMessageAudiencePageVO();
		result.setRecords(records);
		result.setHasMore(hasMore);
		result.setNextCursor(records.isEmpty() ? null : records.get(records.size() - 1).getId());
		return result;
	}

	@Override
	public boolean isCustomerServiceStaff(String tenantId, String staffId) {
		requireTenant(tenantId);
		if (staffId == null || staffId.isBlank()) {
			return false;
		}
		return sysUserMapper.countCustomerServiceStaff(tenantId, staffId) > 0;
	}

	private void requireTenant(String tenantId) {
		String currentTenantId = ArynTenantContextHolder.getTenantId();
		if (tenantId == null || tenantId.isBlank() || !Objects.equals(currentTenantId, tenantId)) {
			throw new ArynBusinessException("消息工作人员查询租户不匹配");
		}
	}

	private int normalizeLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
	}

}
