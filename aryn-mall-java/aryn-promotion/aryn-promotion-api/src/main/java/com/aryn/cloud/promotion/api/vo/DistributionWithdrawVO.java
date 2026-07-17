package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Accessors(chain = true)
@Schema(description = "分销提现展示信息")
public class DistributionWithdrawVO {

	private String id;
	private String withdrawNo;
	private String userId;
	private BigDecimal amount;
	private String status;
	private String accountType;
	private String accountName;

	@Schema(description = "脱敏收款账号")
	private String accountNo;

	private String rejectReason;
	private String remark;
	private LocalDateTime auditTime;
	private String auditBy;
	private String payoutNo;
	private LocalDateTime payoutTime;
	private String payoutBy;
	private LocalDateTime createTime;
	private LocalDateTime updateTime;
}
