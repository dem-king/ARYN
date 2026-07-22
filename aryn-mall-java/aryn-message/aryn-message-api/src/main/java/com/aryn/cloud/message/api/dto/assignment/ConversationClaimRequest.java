package com.aryn.cloud.message.api.dto.assignment;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 客服领取会话请求。 */
@Data
public class ConversationClaimRequest implements Serializable {

	@Size(max = 500)
	private String reason;

}
