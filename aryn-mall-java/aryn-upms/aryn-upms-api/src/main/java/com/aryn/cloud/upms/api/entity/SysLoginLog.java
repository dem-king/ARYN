package com.aryn.cloud.upms.api.entity;

import com.aryn.cloud.common.core.entity.SysLoginLogBase;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Schema(description = "登录日志")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_login_log")
public class SysLoginLog extends Model<SysLoginLog> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "ip地址")
	private String ipAddr;

	@Schema(description = "登录地点")
	private String location;

	@Schema(description = "登录用户")
	private String userName;

	@Schema(description = "状态：0.失败；1.成功；")
	private String status;

	@Schema(description = "信息")
	private String msg;

	@Schema(description = "浏览器")
	private String browser;

	@Schema(description = "操作系统")
	private String os;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private java.time.LocalDateTime createTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

	@Schema(description = "租户id")
	private String tenantId;

	public SysLoginLogBase toBase() {
		SysLoginLogBase base = new SysLoginLogBase();
		base.setId(id);
		base.setIpAddr(ipAddr);
		base.setLocation(location);
		base.setUserName(userName);
		base.setStatus(status);
		base.setMsg(msg);
		base.setBrowser(browser);
		base.setOs(os);
		base.setCreateBy(createBy);
		base.setCreateTime(createTime);
		base.setDelFlag(delFlag);
		base.setTenantId(tenantId);
		return base;
	}

}
