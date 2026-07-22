package com.aryn.cloud.message.api.dto.agent;

import com.aryn.cloud.message.api.enums.AgentPresenceStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/** 客服实时状态请求。 */
@Data
public class AgentPresenceRequest implements Serializable {

	@NotNull
	private AgentPresenceStatus status;

}
