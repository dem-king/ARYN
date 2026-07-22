package com.aryn.cloud.user.dubbo;

import com.aryn.cloud.common.myabtis.tenant.ArynTenantContextHolder;
import com.aryn.cloud.common.security.handler.ArynBusinessException;
import com.aryn.cloud.user.api.dto.UserMessageAudienceRequest;
import com.aryn.cloud.user.api.remote.RemoteMessageAudienceService;
import com.aryn.cloud.user.api.vo.UserMessageAudiencePageVO;
import com.aryn.cloud.user.api.vo.UserMessageRecipientVO;
import com.aryn.cloud.user.mapper.UserInfoMapper;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** 消息域会员受众查询实现。 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteMessageAudienceServiceImpl implements RemoteMessageAudienceService {

	private static final int DEFAULT_LIMIT = 500;

	private static final int MAX_LIMIT = 1000;

	private final UserInfoMapper userInfoMapper;

	@Override
	public UserMessageAudiencePageVO queryRecipients(UserMessageAudienceRequest request) {
		requireTenant(request == null ? null : request.getTenantId());
		int limit = normalizeLimit(request.getLimit());
		List<UserMessageRecipientVO> queried = userInfoMapper.selectMessageRecipients(request, limit + 1);
		List<UserMessageRecipientVO> records = queried == null ? new ArrayList<>() : new ArrayList<>(queried);
		boolean hasMore = records.size() > limit;
		if (hasMore) {
			records.remove(records.size() - 1);
		}
		UserMessageAudiencePageVO result = new UserMessageAudiencePageVO();
		result.setRecords(records);
		result.setHasMore(hasMore);
		result.setNextCursor(records.isEmpty() ? null : records.get(records.size() - 1).getId());
		return result;
	}

	private void requireTenant(String tenantId) {
		String currentTenantId = ArynTenantContextHolder.getTenantId();
		if (tenantId == null || tenantId.isBlank() || !Objects.equals(currentTenantId, tenantId)) {
			throw new ArynBusinessException("消息受众查询租户不匹配");
		}
	}

	private int normalizeLimit(Integer limit) {
		if (limit == null || limit <= 0) {
			return DEFAULT_LIMIT;
		}
		return Math.min(limit, MAX_LIMIT);
	}

}
