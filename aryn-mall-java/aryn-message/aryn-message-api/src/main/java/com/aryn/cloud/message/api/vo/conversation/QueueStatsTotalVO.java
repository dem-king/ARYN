package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 队列统计总计（全租户口径）。
 *
 * <p>在线坐席数/可用容量为全租户值：当前 message_agent 没有队列绑定，不在分组上重复下发。</p>
 */
@Data
public class QueueStatsTotalVO implements Serializable {

	/** 全部队列等待中会话数合计。 */
	private Long waitingCount;

	/** 全部等待会话中最早的等待开始时间；无等待时为 null。 */
	private LocalDateTime oldestWaitingSince;

	/** 在线坐席数（启用且有未过期 presence 心跳）。 */
	private Integer onlineAgentCount;

	/** 可用接待容量（仅启用且 ONLINE 的坐席剩余容量合计）。 */
	private Integer availableCapacity;

}
