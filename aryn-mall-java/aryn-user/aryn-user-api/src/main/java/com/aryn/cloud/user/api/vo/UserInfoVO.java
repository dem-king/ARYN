
package com.aryn.cloud.user.api.vo;

import com.aryn.cloud.common.core.annotation.Desensitization;
import com.aryn.cloud.common.core.desensitization.MobilePhoneDesensitization;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 用户信息VO
 *
 * @author 雨滴kian
 * @date 2022/7/11
 */
@Data
@Schema(description = "用户信息VO")
public class UserInfoVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "昵称")
	private String nickname;

	@Schema(description = "头像")
	private String avatarUrl;

	@Schema(description = "手机号")
	@Desensitization(MobilePhoneDesensitization.class)
	private String phone;

	@Schema(description = "密码")
	private String password;

	@Schema(description = "租户id")
	private String tenantId;

}
