package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 队列统计视图：总计 + 按 queueCode 分组的等待统计。 */
@Data
public class QueueStatsVO implements Serializable {

	private QueueStatsTotalVO total;

	/** 有等待中会话的队列分组，按 oldestWaitingSince 升序；无等待时为空列表。 */
	private List<QueueStatsGroupVO> groups;

}
