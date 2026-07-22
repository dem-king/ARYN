package com.aryn.cloud.message.service;

import com.aryn.cloud.message.api.vo.conversation.ConversationVO;

/** 同租户工作人员一对一私信服务。 */
public interface StaffDirectService {

	ConversationVO getOrCreate(String tenantId, String currentStaffId, String currentStaffName,
			String currentStaffAvatar, String targetStaffId);

}
