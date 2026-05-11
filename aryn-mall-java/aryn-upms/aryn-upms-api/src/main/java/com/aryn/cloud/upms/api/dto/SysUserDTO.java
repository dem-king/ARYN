
package com.aryn.cloud.upms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 系统用户VO
 *
 * @author 雨滴kian
 * @date 2022/7/13
 */
@Data
public class SysUserDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "密码")
	private String password;

	@Schema(description = "新密码")
	private String newPassword;

	@Schema(description = "确认密码")
	private String checkPassword;

}
