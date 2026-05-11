package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 分销用户详情VO
 *
 * @author 雨滴kian
 * @date 2025/4/8
 */
@Data
@Schema(description = "分销用户详情")
public class DistributionUserVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "用户ID")
	private String userId;

	@Schema(description = "用户昵称")
	private String nickname;

	@Schema(description = "用户头像")
	private String avatar;

	@Schema(description = "邀请人ID")
	private String inviterUserId;

	@Schema(description = "累计佣金")
	private BigDecimal totalCommission;

	@Schema(description = "可提现佣金")
	private BigDecimal availableCommission;

	@Schema(description = "已提现佣金")
	private BigDecimal withdrawnCommission;

	@Schema(description = "冻结佣金")
	private BigDecimal frozenCommission;

	@Schema(description = "下级人数")
	private Integer subordinateCount;

	@Schema(description = "状态：0启用 1禁用")
	private String status;

}
