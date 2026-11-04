package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/** 客服会话分配审计日志视图（主管视图的分配日志抽屉使用）。 */
@Data
public class ConversationAssignmentLogVO implements Serializable {

	/** 分配动作：AUTO_ASSIGN/CLAIM/TRANSFER/FORCE_ASSIGN/CLOSE/REOPEN/INTERVENE。 */
	private String actionType;

	/** 原负责客服 ID（自动分配、领取为空）。 */
	private String fromStaffId;

	/** 目标客服 ID（关闭等动作可能为空）。 */
	private String toStaffId;

	/** 操作人类型：SYSTEM/SYS_USER/MALL_USER。 */
	private String operatorType;

	/** 操作人 ID。 */
	private String operatorId;

	/** 操作原因。 */
	private String reason;

	/** 操作时间。 */
	private LocalDateTime createTime;

}
