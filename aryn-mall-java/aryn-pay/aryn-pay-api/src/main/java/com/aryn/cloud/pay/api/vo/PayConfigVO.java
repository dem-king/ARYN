/** 
  * Copyright (c) 2025 天启雨数科技有限公司 
  * All rights reserved. 
  * <p> 
  * 注意： 
  * 本项目源代码由天启雨数科技有限公司原创开发，版权所有。 
 
  */
package com.aryn.cloud.pay.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 支付配置安全读取模型。不得包含私钥、密钥、密文或服务器路径。
 */
@Data
@Schema(description = "支付配置")
public class PayConfigVO {

	@Schema(description = "主键")
	private String id;

	@Schema(description = "支付类型：1.微信；2.支付宝；")
	private String type;

	@Schema(description = "应用ID")
	private String appId;

	@Schema(description = "商户号/收款方PID")
	private String mchId;

	@Schema(description = "创建人")
	private String createBy;

	@Schema(description = "修改人")
	private String updateBy;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

	@Schema(description = "修改时间")
	private LocalDateTime updateTime;

	@Schema(description = "支付端类型：0-小程序，1-App，2-H5，3-PC，4-公众号")
	private String terminalType;

	@Schema(description = "租户id")
	private String tenantId;

	@Schema(description = "是否启用：0.停用；1.启用；")
	private String enabled;

	@Schema(description = "当前生效的身份版本ID")
	private String activeRevisionId;

	@Schema(description = "并发版本号")
	private Integer configVersion;

	@Schema(description = "活动版本是否已具备启用所需材料")
	private Boolean materialReady;

	@Schema(description = "活动版本本地检查结果：0.未检查；1.通过；2.失败")
	private String checkStatus;

}
