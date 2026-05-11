
package com.aryn.cloud.upms.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;

@Data
public class SysTenantMenuDTO {

	@Schema(description = "菜单权限集合")
	private List<String> menuIds;

	@Schema(description = "租户ID")
	private String tenantId;

}
