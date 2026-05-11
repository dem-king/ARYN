package com.aryn.cloud.upms.api.entity;

import com.aryn.cloud.common.core.entity.SysLogBase;
import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@Schema(description = "操作日志")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_log")
public class SysLog extends Model<SysLog> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "ip地址")
	private String ipAddr;

	@Schema(description = "请求标题")
	private String title;

	@Schema(description = "请求方式")
	private String requestMethod;

	@Schema(description = "请求URI")
	private String requestUri;

	@Schema(description = "请求数据")
	private String requestParams;

	@Schema(description = "请求时长")
	private Long requestTime;

	@Schema(description = "操作地点")
	private String location;

	@Schema(description = "操作方法")
	private String method;

	@Schema(description = "操作用户")
	private String userName;

	@Schema(description = "状态：0.失败；1.成功；")
	private String status;

	@Schema(description = "异常信息")
	private String exMsg;

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

	public SysLogBase toBase() {
		SysLogBase base = new SysLogBase();
		base.setId(id);
		base.setIpAddr(ipAddr);
		base.setTitle(title);
		base.setRequestMethod(requestMethod);
		base.setRequestUri(requestUri);
		base.setRequestParams(requestParams);
		base.setRequestTime(requestTime);
		base.setLocation(location);
		base.setMethod(method);
		base.setUserName(userName);
		base.setStatus(status);
		base.setExMsg(exMsg);
		base.setCreateBy(createBy);
		base.setCreateTime(createTime);
		base.setDelFlag(delFlag);
		base.setTenantId(tenantId);
		return base;
	}

}
