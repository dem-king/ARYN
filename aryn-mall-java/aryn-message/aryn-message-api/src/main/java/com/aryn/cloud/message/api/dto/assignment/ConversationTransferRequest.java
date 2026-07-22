package com.aryn.cloud.message.api.dto.assignment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 客服转交会话请求。 */
@Data
public class ConversationTransferRequest implements Serializable {

	@NotBlank
	private String targetStaffId;

	@Size(max = 500)
	private String reason;

}
