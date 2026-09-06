package com.aryn.cloud.user.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SocialUserUnbindDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String openId;

	private String mallUserId;

	private String appId;

}
