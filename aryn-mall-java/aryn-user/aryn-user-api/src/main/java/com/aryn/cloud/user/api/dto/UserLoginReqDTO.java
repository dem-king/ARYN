
package com.aryn.cloud.user.api.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class UserLoginReqDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	private String appId;

	private String jsCode;

	private String platformType;

	private String phone;

	private String password;

	private String code;

	private String wxUserId;

}
