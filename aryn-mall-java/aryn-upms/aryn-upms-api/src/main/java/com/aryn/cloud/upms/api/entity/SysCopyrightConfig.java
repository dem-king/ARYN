package com.aryn.cloud.upms.api.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 版权配置（租户级，一行一端）。
 *
 * <p>三端版权/备案信息此前写死在前端代码与语言包里，改一次要发版。本表把它收敛为
 * 租户级配置：{@code clientType} 区分用户端（C 端商城）、商户端（管理后台）、平台端
 * （管理后台登录页）。名称与 Logo 留空表示跟随租户品牌（sys_tenant.name/logoUrl），
 * 避免顶栏与页脚出现两套品牌。</p>
 */
@Data
@Schema(description = "版权配置")
@EqualsAndHashCode(callSuper = true)
@TableName(value = "sys_copyright_config")
public class SysCopyrightConfig extends Model<SysCopyrightConfig> {

	private static final long serialVersionUID = 1L;

	/** 用户端：C 端商城（小程序/H5） */
	public static final String CLIENT_APP = "app";

	/** 商户端：管理后台登录后的页脚 */
	public static final String CLIENT_MERCHANT = "merchant";

	/** 平台端：管理后台登录页（登录前无会话，固定取平台租户配置） */
	public static final String CLIENT_PLATFORM = "platform";

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "端类型：app 用户端；merchant 商户端；platform 平台端")
	private String clientType;

	@Schema(description = "Logo（留空跟随租户品牌）")
	private String logoUrl;

	@Schema(description = "名称（留空跟随租户品牌）")
	private String name;

	@Schema(description = "版权信息")
	private String copyrightText;

	@Schema(description = "版权链接")
	private String copyrightLink;

	@Schema(description = "ICP备案号")
	private String icp;

	@Schema(description = "ICP网站链接")
	private String icpLink;

	@Schema(description = "登录页标题（仅平台端）")
	private String loginTitle;

	@Schema(description = "登录页副标题（仅平台端）")
	private String loginSubtitle;

	@Schema(description = "登录页图标（仅平台端）")
	private String loginIcon;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建人")
	private String createBy;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改人")
	private String updateBy;

	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@TableField(fill = FieldFill.UPDATE)
	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@TableLogic
	@TableField(fill = FieldFill.INSERT)
	@Schema(description = "逻辑删除：0.显示；1.隐藏；")
	private String delFlag;

	@Schema(description = "租户id")
	private String tenantId;

}