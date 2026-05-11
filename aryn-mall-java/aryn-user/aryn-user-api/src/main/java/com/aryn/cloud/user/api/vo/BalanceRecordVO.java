package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 余额变动记录VO
 *
 * @author 雨滴kian
 */
@Data
public class BalanceRecordVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "会员昵称")
	private String nickname;

	@Schema(description = "变动类型：1-充值；2-消费；3-调整")
	private String changeType;

	@Schema(description = "变动金额")
	private BigDecimal changeAmount;

	@Schema(description = "变动后余额")
	private BigDecimal balanceAfter;

	@Schema(description = "触发场景")
	private String triggerScene;

	@Schema(description = "备注")
	private String remark;

	@Schema(description = "创建时间")
	private LocalDateTime createTime;

}
