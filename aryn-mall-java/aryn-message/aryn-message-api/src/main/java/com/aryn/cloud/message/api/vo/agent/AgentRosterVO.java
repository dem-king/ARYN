package com.aryn.cloud.message.api.vo.agent;

import lombok.Data;

import java.io.Serializable;

/**
 * 客服坐席名单项：坐席配置 + 实时接待状态 + 负载，供工作台转交/强制分配选人。
 *
 * <p>{@code staffName}/{@code staffAvatar} 来自 upms 人员快照（可能为空，前端回退 staffId）；
 * {@code enabled}/{@code autoAccept} 沿用消息域 char(1) 字符串约定（"1"=是，"0"=否）。</p>
 */
@Data
public class AgentRosterVO implements Serializable {

	private String staffId;
	private String staffName;
	private String staffAvatar;
	private String presenceStatus;
	private String enabled;
	private String autoAccept;
	private Integer currentActiveCount;
	private Integer maxActiveCount;

	/** 是否可承接新会话：坐席启用、实时状态 ONLINE 且未达接待上限。 */
	private Boolean available;

}
