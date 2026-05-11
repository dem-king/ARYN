
package com.aryn.cloud.upms.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 租户分配菜单
 *
 * @author 雨滴kian
 * @since 2022/2/10 11:00
 */
@Data
@Schema(description = "租户分配菜单")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_tenant_menu")
public class SysTenantMenu extends Model<SysTenantMenu> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "菜单ID")
	private String menuId;

	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "创建人")
	private String createBy;

}
