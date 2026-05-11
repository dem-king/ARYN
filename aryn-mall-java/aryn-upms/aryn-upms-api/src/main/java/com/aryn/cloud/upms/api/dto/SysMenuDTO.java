
package com.aryn.cloud.upms.api.dto;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 系统菜单
 *
 * @author 雨滴kian
 * @date 2022/7/30
 */
@Data
public class SysMenuDTO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "父菜单ID")
	private String parentId;

	@Schema(description = "父菜单名称")
	private String parentName;

	@Schema(description = "权限前缀")
	@TableField(exist = false)
	private String permissionPrefix;

}
