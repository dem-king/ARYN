/**
 * Copyright (c) 2025 天启雨数科技有限公司
 * All rights reserved.
 *
 * 注意：
 * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。
 */

package com.aryn.cloud.user.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 三方平台账号
 *
 * @author 雨滴kian
 * @date 2026-04-05 00:20:56
 */
@Data
@Schema(description = "三方平台账号")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "social_account")
public class SocialAccount extends Model<SocialAccount> {

	private static final long serialVersionUID = 1L;

	/**
	 * 主键
	 */
	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/**
	 * 账号类型
	 */
	@Schema(description = "账号类型")
	private String type;

	/**
	 * 账号
	 */
	@Schema(description = "账号")
	private String appId;

	/**
	 * 密钥
	 */
	@Schema(description = "密钥")
	private String appSecret;

	/**
	 * 创建时间
	 */
	@Schema(description = "创建时间")
	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;

	/**
	 * 更新时间
	 */
	@Schema(description = "更新时间")
	@TableField(fill = FieldFill.UPDATE)
	private LocalDateTime updateTime;

	/**
	 * 逻辑删除：0.显示；1.隐藏；
	 */
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

	/**
	 * 创建人
	 */
	@Schema(description = "创建人")
	@TableField(fill = FieldFill.INSERT)
	private String createBy;

	/**
	 * 修改人
	 */
	@Schema(description = "修改人")
	@TableField(fill = FieldFill.UPDATE)
	private String updateBy;

	/**
	 * 租户id
	 */
	@Schema(description = "租户id")
	private String tenantId;

}
