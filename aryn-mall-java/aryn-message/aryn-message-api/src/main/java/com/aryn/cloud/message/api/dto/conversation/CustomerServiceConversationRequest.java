package com.aryn.cloud.message.api.dto.conversation;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 创建或获取客服会话请求。 */
@Data
public class CustomerServiceConversationRequest implements Serializable {

	@Size(max = 64)
	private String queueCode;

	@Size(max = 4000)
	private String contextPayload;

}
