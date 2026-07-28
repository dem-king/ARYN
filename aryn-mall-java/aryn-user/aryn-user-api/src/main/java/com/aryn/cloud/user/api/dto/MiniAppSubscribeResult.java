package com.aryn.cloud.user.api.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** 微信订阅消息发送结果，区分终止错误与可恢复错误。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MiniAppSubscribeResult implements Serializable {

	private static final long serialVersionUID = 1L;

	private String status;
	private String errorCode;
	private String errorMessage;

	public static MiniAppSubscribeResult success() {
		return new MiniAppSubscribeResult("SUCCESS", null, null);
	}

	public static MiniAppSubscribeResult terminal(String errorCode, String errorMessage) {
		return new MiniAppSubscribeResult("TERMINAL", errorCode, errorMessage);
	}

	public static MiniAppSubscribeResult retryable(String errorCode, String errorMessage) {
		return new MiniAppSubscribeResult("RETRYABLE", errorCode, errorMessage);
	}

}
