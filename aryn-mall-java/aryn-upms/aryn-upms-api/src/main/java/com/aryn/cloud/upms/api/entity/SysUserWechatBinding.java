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
 * 员工微信绑定
 */
@Data
@Schema(description = "员工微信绑定")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_user_wechat_binding")
public class SysUserWechatBinding extends Model<SysUserWechatBinding> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "员工ID")
	private String userId;

	@Schema(description = "小程序AppID")
	private String appId;

	@Schema(description = "openid")
	private String openid;

	@Schema(description = "绑定状态：1有效 0解绑")
	private String status;

	@Schema(description = "绑定时间")
	private LocalDateTime bindTime;

	@Schema(description = "租户ID")
	private String tenantId;

	@Schema(description = "创建人")
	private String createBy;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;
}