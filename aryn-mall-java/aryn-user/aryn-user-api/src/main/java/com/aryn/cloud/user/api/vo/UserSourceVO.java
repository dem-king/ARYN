package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户来源。
 */
@Data
@Schema(description = "用户来源")
public class UserSourceVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "来源编码")
	private String source;

}
