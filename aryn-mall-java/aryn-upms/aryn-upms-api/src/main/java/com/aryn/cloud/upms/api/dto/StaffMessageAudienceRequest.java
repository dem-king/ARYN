package com.aryn.cloud.upms.api.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/** 消息通知工作人员受众游标请求。 */
@Data
public class StaffMessageAudienceRequest implements Serializable {

	private String tenantId;
	private String cursor;
	private Integer limit;
	private List<String> userIds;
	private List<String> roleIds;
	private List<String> deptIds;
	private Boolean customerServiceOnly;

	/**
	 * 按菜单权限码筛人：命中任一权限即算受众。
	 *
	 * <p>与 roleIds 的区别是「按能不能干活筛」而不是「按什么头衔筛」——
	 * 审核类通知应发给真正持有该审核权限的人（授权可能挂在运营角色上），
	 * 只按角色码筛会漏掉非管理员角色。
	 */
	private List<String> permissions;

}
