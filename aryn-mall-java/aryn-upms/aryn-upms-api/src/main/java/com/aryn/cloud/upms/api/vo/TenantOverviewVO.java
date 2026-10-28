package com.aryn.cloud.upms.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 平台侧租户概览。
 *
 * <p>统计项跨模块取值，单个模块不可用时对应字段为 null，不影响其余数据展示。
 */
@Data
@Schema(description = "租户概览")
public class TenantOverviewVO implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "租户ID")
	private String id;

	@Schema(description = "租户名称")
	private String name;

	@Schema(description = "套餐ID")
	private String packageId;

	@Schema(description = "套餐名称")
	private String packageName;

	@Schema(description = "状态：0.正常；1.停用")
	private String status;

	@Schema(description = "业务模式：1综合（个人+船供并存） 2纯零售")
	private String businessMode;

	@Schema(description = "授权开始时间")
	private LocalDateTime authBeginTime;

	@Schema(description = "授权结束时间")
	private LocalDateTime authEndTime;

	@Schema(description = "剩余天数；负数表示已过期")
	private Long remainingDays;

	@Schema(description = "管理端账号数")
	private Long adminUserCount;

	@Schema(description = "商城会员数")
	private Long mallUserCount;

	@Schema(description = "商品数")
	private Long goodsCount;

	@Schema(description = "订单数")
	private Long orderCount;

	@Schema(description = "素材数")
	private Long materialCount;

	@Schema(description = "素材占用字节数")
	private Long storageBytes;

	@Schema(description = "当前在线会话数")
	private Long onlineSessionCount;

	@Schema(description = "最近登录时间")
	private LocalDateTime lastLoginTime;

}
