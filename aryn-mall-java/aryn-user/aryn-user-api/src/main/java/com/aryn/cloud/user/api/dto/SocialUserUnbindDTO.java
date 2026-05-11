package com.aryn.cloud.user.api.dto;

import lombok.Data;

@Data
public class SocialUserUnbindDTO {

	private String openId;

	private String mallUserId;

	private String appId;

}
