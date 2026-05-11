package com.aryn.cloud.promotion.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分销用户注册/绑定邀请关系请求
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Data
@Schema(description = "分销用户注册请求")
public class DistributionUserRegisterDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@NotBlank(message = "用户ID不能为空")
	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "用户昵称")
	private String nickname;

	@Schema(description = "用户头像")
	private String avatar;

	@Schema(description = "邀请人用户ID")
	private String inviterUserId;

}
