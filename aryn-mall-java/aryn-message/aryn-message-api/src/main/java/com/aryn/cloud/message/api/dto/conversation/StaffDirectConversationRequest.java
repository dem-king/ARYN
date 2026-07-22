package com.aryn.cloud.message.api.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serializable;

/** 创建或获取工作人员一对一私信请求。 */
@Data
public class StaffDirectConversationRequest implements Serializable {

	@NotBlank
	private String targetStaffId;

}
