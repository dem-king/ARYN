
package com.aryn.cloud.upms.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 菜单
 *
 * @author 雨滴kian
 * @since 2022/2/18 11:46
 */
@Data
public class MenuVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "菜单名称")
	private String name;

	@Schema(description = "菜单权限")
	private String permission;

	@Schema(description = "URL")
	private String path;

	@Schema(description = "重定向url")
	private String redirect;

	@Schema(description = "父菜单ID")
	private String parentId;

	@Schema(description = "图标")
	private String icon;

	@Schema(description = "页面地址")
	private String component;

	@Schema(description = "排序")
	private Integer sort;

	@Schema(description = "类型: 0.菜单; 1.按钮;")
	private String type;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "外链状态：0.否；1.是；")
	private String outerStatus;

	@Schema(description = "应用key")
	private String applicationKey;

}
