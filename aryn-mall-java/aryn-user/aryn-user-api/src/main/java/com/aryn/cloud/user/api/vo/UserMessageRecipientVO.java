package com.aryn.cloud.user.api.vo;

import lombok.Data;

import java.io.Serializable;

/** 消息通知所需的最小会员快照。 */
@Data
public class UserMessageRecipientVO implements Serializable {

	private String id;
	private String nickname;
	private String avatarUrl;
	private String memberLevelId;

}
