package com.aryn.cloud.message.api.dto.assignment;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/** 关闭会话请求。 */
@Data
public class ConversationCloseRequest implements Serializable {

	@Size(max = 500)
	private String reason;

}
