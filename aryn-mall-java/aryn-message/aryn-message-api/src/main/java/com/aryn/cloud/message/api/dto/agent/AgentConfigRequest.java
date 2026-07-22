package com.aryn.cloud.message.api.dto.agent;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;

/** 客服坐席配置请求。 */
@Data
public class AgentConfigRequest implements Serializable {

	@NotBlank
	private String staffId;

	@NotNull
	private Boolean enabled;

	@NotNull
	private Boolean autoAccept;

	@NotNull
	@Min(1)
	@Max(100)
	private Integer maxActiveCount;

}
