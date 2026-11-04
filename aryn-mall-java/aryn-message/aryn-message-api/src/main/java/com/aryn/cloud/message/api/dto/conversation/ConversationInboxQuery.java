package com.aryn.cloud.message.api.dto.conversation;

import lombok.Data;

import java.io.Serializable;

/** 本人会话列表查询。 */
@Data
public class ConversationInboxQuery implements Serializable {

	/**
	 * 状态过滤：OPEN（WAITING/ASSIGNED/ACTIVE）或 CLOSED；缺省不过滤。
	 *
	 * <p>「我的会话」「历史会话」「全部会话」三个页签分别传 OPEN、CLOSED、空。</p>
	 */
	private String status;

	/** 不透明游标：服务端生成，前端原样回传（内部编码最近活跃时间 + 会话 ID）。 */
	private String cursor;

	private Integer limit;

}
