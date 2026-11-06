package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 客户换绑日志视图
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "客户换绑日志视图")
public class SalesCustomerBindLogVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "客户会员ID")
	private String customerUserId;

	@Schema(description = "原销售ID（可空）")
	private String oldSalesStaffId;

	@Schema(description = "原销售姓名")
	private String oldSalesStaffName;

	@Schema(description = "新销售ID")
	private String newSalesStaffId;

	@Schema(description = "新销售姓名")
	private String newSalesStaffName;

	@Schema(description = "变更来源：SHARE/QRCODE/ADMIN/TRANSFER/CLAIM")
	private String bindSource;

	@Schema(description = "变更原因")
	private String reason;

	@Schema(description = "操作人ID")
	private String operatorId;

	@Schema(description = "操作人姓名")
	private String operatorName;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
