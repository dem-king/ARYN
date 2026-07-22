package com.aryn.cloud.message.api.vo.agent;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 客服坐席配置与实时状态视图。 */
@Data
public class AgentVO implements Serializable {

	private String id;
	private String staffId;
	private String enabled;
	private String autoAccept;
	private Integer maxActiveCount;
	private Integer currentActiveCount;
	private LocalDateTime lastAssignedTime;
	private String presenceStatus;

}
