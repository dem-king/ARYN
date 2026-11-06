package com.aryn.cloud.message.api.vo.agent;

import lombok.Data;

import java.io.Serializable;

/**
 * 租户级坐席容量快照（队列统计用）。
 *
 * <p>当前没有坐席-队列绑定，因此只提供全租户口径；只读 message_agent + Redis presence，
 * 不查 upms，避免队列统计的高频轮询触发远程调用。</p>
 */
@Data
public class AgentCapacityVO implements Serializable {

	/** 在线坐席数：启用且有未过期 presence 心跳（ONLINE/BUSY/PAUSED）。 */
	private int onlineAgentCount;

	/** 可用接待容量：仅启用且 ONLINE 的坐席，Σ max(0, max_active_count - current_active_count)。 */
	private int availableCapacity;

}
