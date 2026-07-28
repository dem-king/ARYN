package com.aryn.cloud.upms.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/** TOB 员工与配送小程序 OpenID 的绑定关系。 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user_wechat_binding")
public class SysUserWechatBinding extends Model<SysUserWechatBinding> {

	@TableId(type = IdType.ASSIGN_ID)
	private String id;
	private String userId;
	private String appId;
	private String openid;
	private String status;
	private LocalDateTime boundAt;
	private LocalDateTime unboundAt;
	private String tenantId;
	@TableField(fill = FieldFill.INSERT)
	private String createBy;
	@TableField(fill = FieldFill.UPDATE)
	private String updateBy;
	@TableField(fill = FieldFill.INSERT)
	private LocalDateTime createTime;
	@TableField(fill = FieldFill.UPDATE)
	private LocalDateTime updateTime;
	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	private String delFlag;

}
