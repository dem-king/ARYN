package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户归属视图
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "客户归属视图")
public class SalesCustomerVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "归属记录ID")
	private String id;

	@Schema(description = "客户会员ID")
	private String customerUserId;

	@Schema(description = "客户昵称")
	private String nickname;

	@Schema(description = "客户头像")
	private String avatar;

	@Schema(description = "归属销售ID")
	private String salesStaffId;

	@Schema(description = "归属销售姓名")
	private String salesStaffName;

	@Schema(description = "绑定来源")
	private String bindSource;

	@Schema(description = "绑定时间")
	private LocalDateTime bindTime;

	@Schema(description = "待转交认领销售ID")
	private String pendingClaimStaffId;

	@Schema(description = "待转交认领销售姓名")
	private String pendingClaimStaffName;

	@Schema(description = "待转交认领状态：0待首单 1已转交 2已失效")
	private String claimStatus;

	@Schema(description = "待转交认领失效时间")
	private LocalDateTime claimExpireTime;

}
