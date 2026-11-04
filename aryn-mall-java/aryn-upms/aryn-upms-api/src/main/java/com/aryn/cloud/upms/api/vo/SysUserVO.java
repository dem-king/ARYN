package com.aryn.cloud.upms.api.vo;

import cn.hutool.core.bean.BeanUtil;
import com.aryn.cloud.common.core.annotation.Desensitization;
import com.aryn.cloud.common.core.desensitization.MobilePhoneDesensitization;
import com.aryn.cloud.upms.api.entity.SysUser;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 系统用户 HTTP 响应对象。
 *
 * <p>用户信息接口（当前用户信息、用户详情）此前直接返回 {@link SysUser} 实体，
 * 其中包含 BCrypt 密码 hash，任何具备列表/详情权限的账号都能拿到其它用户的 hash
 * 做离线猜解。HTTP 响应统一使用本 VO，<b>不包含 password 字段</b>；
 * 内部认证链路（Dubbo 远程查询 / 密码校验）继续使用实体，不受影响。</p>
 *
 * <p>新增响应字段时加到这里，不要直接返回实体。</p>
 *
 * @author aryn
 * @since 2026/10/28
 */
@Data
@Schema(description = "系统用户（不含密码）")
public class SysUserVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "用户名")
	private String username;

	@Schema(description = "邮箱")
	private String email;

	@Schema(description = "用户昵称")
	private String nickname;

	@Schema(description = "头像")
	private String avatar;

	@Schema(description = "部门ID")
	private String deptId;

	@Schema(description = "部门名称（关联查询字段）")
	private String deptName;

	@Schema(description = "手机号")
	@Desensitization(MobilePhoneDesensitization.class)
	private String phone;

	@Schema(description = "状态：0.正常；1.停用；")
	private String status;

	@Schema(description = "创建人")
	private String createBy;

	@Schema(description = "修改人")
	private String updateBy;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "租户类型：1.平台租户；2.普通租户")
	private String tenantType;

	@Schema(description = "是否平台管理员")
	private Boolean platformAdmin;

	@Schema(description = "账号类型：0.系统主账户（管理全部店铺）")
	private String type;

	@Schema(description = "权限标识")
	private Set<String> permissions;

	@Schema(description = "角色")
	private List<String> roles;

	@Schema(description = "配送资格只读摘要：是否拥有受保护配送角色（delivery_staff）")
	private Boolean deliveryQualification;

	/**
	 * 实体转 HTTP 响应对象（丢弃 password，其余字段保持原样）。
	 */
	public static SysUserVO from(SysUser sysUser) {
		if (sysUser == null) {
			return null;
		}
		SysUserVO vo = new SysUserVO();
		BeanUtil.copyProperties(sysUser, vo);
		return vo;
	}

}
