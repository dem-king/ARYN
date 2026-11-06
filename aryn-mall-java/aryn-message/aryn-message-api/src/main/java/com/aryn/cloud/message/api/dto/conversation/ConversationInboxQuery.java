package com.aryn.cloud.message.api.dto.conversation;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 本人会话列表查询。 */
@Data
public class ConversationInboxQuery implements Serializable {

	/**
	 * 状态过滤：OPEN（WAITING/ASSIGNED/ACTIVE）或 CLOSED；缺省不过滤。
	 *
	 * <p>「我的会话」「历史会话」「全部会话」三个页签分别传 OPEN、CLOSED、空。</p>
	 */
	private String status;

	/**
	 * 精确状态集合：WAITING / ASSIGNED / ACTIVE / CLOSED。
	 *
	 * <p>非空时覆盖 {@link #status}（页签语义让位于精细筛选，同时传不报错）；空集合视同未传。</p>
	 */
	private List<String> statuses;

	/**
	 * 关键词：会员昵称快照 / 会员 ID / 最后消息摘要的包含匹配。
	 *
	 * <p>服务端 trim 后为空的忽略；长度上限 64 字符；LIKE 通配符按字面量转义（SQL 侧 ESCAPE '!'）。</p>
	 */
	private String keyword;

	/** 负责人过滤：精确匹配 assigned_staff_id；空忽略。 */
	private String assignedStaffId;

	/** 队列过滤：精确匹配 queue_code；空忽略（注意本接口缺省不按队列过滤，与 /waiting 默认 DEFAULT 不同）。 */
	private String queueCode;

	/**
	 * 只看有未读的会话。
	 *
	 * <p>口径：staff 接口为调用者本人未读；admin 接口为「负责客服未读」（存在未读的 ACTIVE SYS_USER
	 * 参与者）。为 true 时服务端强制排除 CLOSED 会话。</p>
	 */
	private Boolean unreadOnly;

	/** 不透明游标：服务端生成，前端原样回传（内部编码最近活跃时间 + 会话 ID）。 */
	private String cursor;

	private Integer limit;

}
