package com.aryn.cloud.message.api.dto.conversation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 客服主动联系会员请求。 */
@Data
public class StaffInitiateConversationRequest implements Serializable {

	@NotBlank
	private String customerId;

	@Size(max = 64)
	private String queueCode;

	@Size(max = 4000)
	private String contextPayload;

}
