package com.aryn.cloud.upms.api.remote;

import com.aryn.cloud.upms.api.dto.StaffMessageAudienceRequest;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;

/** 消息域工作人员受众与客服资格查询契约。 */
public interface RemoteMessageStaffService {

	StaffMessageAudiencePageVO queryRecipients(StaffMessageAudienceRequest request);

	boolean isCustomerServiceStaff(String tenantId, String staffId);

}
