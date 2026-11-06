package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 小程序「我的客户」行
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "我的客户")
public class AppSalesCustomerVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "客户会员ID")
	private String customerUserId;

	@Schema(description = "客户昵称")
	private String nickname;

	@Schema(description = "客户头像")
	private String avatar;

	@Schema(description = "绑定来源")
	private String bindSource;

	@Schema(description = "绑定时间")
	private LocalDateTime bindTime;

}
