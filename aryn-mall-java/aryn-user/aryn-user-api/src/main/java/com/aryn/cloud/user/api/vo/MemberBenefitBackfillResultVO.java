package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 会员专属优惠券补发结果。
 */
@Data
@Schema(description = "会员专属优惠券补发结果")
public class MemberBenefitBackfillResultVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "关联等级数")
	private int levelCount;

	@Schema(description = "匹配会员数")
	private int userCount;

	@Schema(description = "发放成功数")
	private int successCount;

	@Schema(description = "发放失败数")
	private int failedCount;

}
