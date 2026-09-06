package com.aryn.cloud.user.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class SocialUserBindDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String id;

	private String mallUserId;

	private String appId;

}
