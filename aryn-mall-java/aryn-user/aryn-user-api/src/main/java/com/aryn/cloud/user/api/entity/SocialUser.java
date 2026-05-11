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
 * 三方平台用户
 *
 * @author 雨滴kian
 * @date 2026-04-05 00:21:22
 */
@Data
@Schema(description = "三方平台用户")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "social_user")
public class SocialUser extends Model<SocialUser> {

	private static final long serialVersionUID = 1L;

	/**
	 * 主键
	 */
	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	/**
	 * 账号ID
	 */
	@Schema(description = "账号ID")
	private String socialAccountId;

	/**
	 * appID
	 */
	@Schema(description = "appID")
	private String appId;

	/**
	 * openid
	 */
	@Schema(description = "openid")
	private String openId;

	/**
	 * 会话密钥
	 */
	@Schema(description = "会话密钥")
	private String sessionKey;

	/**
	 * 用户在开放平台的唯一标识符，若当前小程序已绑定到微信开放平台帐号下会返回
	 */
	@Schema(description = "用户在开放平台的唯一标识符，若当前小程序已绑定到微信开放平台帐号下会返回")
	private String unionid;

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
	 * 昵称
	 */
	@Schema(description = "昵称")
	private String nickname;

	/**
	 * 头像
	 */
	@Schema(description = "头像")
	private String avatarUrl;

	/**
	 * 手机号码
	 */
	@Schema(description = "手机号码")
	private String phone;

	/**
	 * 商城用户主键
	 */
	@Schema(description = "商城用户主键")
	private String mallUserId;

	/**
	 * 租户id
	 */
	@Schema(description = "租户id")
	private String tenantId;

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

}
