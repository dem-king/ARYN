
package com.aryn.cloud.pay.api.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.activerecord.Model;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 支付身份版本。渠道、商户、应用、环境一经创建不可修改；换商户必须新增版本。
 */
@Data
@Schema(description = "支付身份版本")
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName(value = "pay_config_revision")
public class PayConfigRevision extends Model<PayConfigRevision> {

	@Schema(description = "主键")
	@TableId(type = IdType.ASSIGN_ID)
	private String id;

	@Schema(description = "支付配置路由ID")
	private String configId;

	@Schema(description = "版本序号")
	private Integer revisionNo;

	@Schema(description = "支付渠道：1.微信；2.支付宝；")
	private String channel;

	@Schema(description = "支付端类型")
	private String terminalType;

	@Schema(description = "应用ID")
	private String appId;

	@Schema(description = "商户号/收款方PID")
	private String mchId;

	@Schema(description = "环境：prod/sandbox")
	private String environment;

	@Schema(description = "微信支付公钥ID")
	private String wxPublicKeyId;

	/**
	 * 支付宝小程序买家身份模式：user_id/open_id；其它渠道为空。
	 */
	@Schema(description = "支付宝小程序买家身份模式：user_id/open_id")
	private String buyerIdentityMode;

	@Schema(description = "材料引用 JSON：用途 -> 材料ID")
	private String materialRefs;

	@Schema(description = "身份指纹，用于迁移幂等")
	private String identityHash;

	@Schema(description = "本地检查结果：0.未检查；1.通过；2.失败")
	private String checkStatus;

	@Schema(description = "本地检查摘要，不含密钥内容")
	private String checkSummary;

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
