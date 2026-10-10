package com.aryn.cloud.upms.api.remote;

import com.aryn.cloud.upms.api.dto.StaffMessageAudienceRequest;
import com.aryn.cloud.upms.api.vo.StaffMessageAudiencePageVO;
import com.aryn.cloud.upms.api.vo.StaffMessageRecipientVO;

import java.util.List;

/** 消息域工作人员受众与客服资格查询契约。 */
public interface RemoteMessageStaffService {

	StaffMessageAudiencePageVO queryRecipients(StaffMessageAudienceRequest request);

	/**
	 * 按角色编码查询租户内正常状态工作人员（业务通知圈定管理端收件人用，如租户管理员）。
	 * @param tenantId 租户（须与当前租户上下文一致）
	 * @param roleCode 角色编码（如 ROLE_ADMIN）
	 * @return 收件人列表（id/nickname），无匹配角色时返回空列表
	 */
	List<StaffMessageRecipientVO> queryRecipientsByRoleCode(String tenantId, String roleCode);

	boolean isCustomerServiceStaff(String tenantId, String staffId);

}
