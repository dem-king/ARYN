
package com.aryn.cloud.upms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * @author 雨滴kian
 */
@Data
public class SysRoleMenuDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "角色ID")
	private String roleId;

	@Schema(description = "菜单权限集合")
	private List<String> menuIds;

	@Schema(description = "租户ID")
	private String tenantId;

}
