package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 队列等待统计分组（只含有等待中会话的队列）。 */
@Data
public class QueueStatsGroupVO implements Serializable {

	private String queueCode;

	/** 该队列等待中会话数（CUSTOMER_SERVICE + WAITING + 未分配）。 */
	private Long waitingCount;

	/** 该队列最久等待开始时间（MIN(create_time)，与待领取池排序一致）。 */
	private LocalDateTime oldestWaitingSince;

}
