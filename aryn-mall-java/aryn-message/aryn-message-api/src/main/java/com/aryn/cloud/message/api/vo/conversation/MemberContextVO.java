package com.aryn.cloud.message.api.vo.conversation;

import lombok.Data;

import java.io.Serializable;

/** 会话上下文中的会员资料（来自会话参与者快照，不新增用户域接口）。 */
@Data
public class MemberContextVO implements Serializable {

	/** 会员 ID（会话 customer_id）；STAFF_DIRECT 私信会话为空。 */
	private String id;

	/** 会员昵称：参与者快照缺失时回退会员 ID。 */
	private String name;

	/** 会员头像 URL；缺失为 null。 */
	private String avatar;

}
