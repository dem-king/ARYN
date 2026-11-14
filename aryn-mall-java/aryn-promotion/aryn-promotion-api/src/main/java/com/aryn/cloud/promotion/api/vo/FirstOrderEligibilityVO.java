package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 船供/企业首单资格判定结果。
 *
 * <p>首单权益按主体（船或采购主体）算一次，不按成员账号重复算。
 * 与个人新人价不同：只认该主体下是否已有成功支付订单，不校验注册时间。
 * 任何远程/数据异常按不合格返回（fail-closed）。
 *
 * @author aryn
 * @since 2026/11/13
 */
@Data
@Schema(description = "首单资格判定结果")
public class FirstOrderEligibilityVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	public static final String SUBJECT_SHIP = "1";

	public static final String SUBJECT_BUYER_ORG = "2";

	@Schema(description = "是否具备首单资格")
	private Boolean eligible;

	@Schema(description = "主体类型：1船 2采购主体")
	private String subjectType;

	@Schema(description = "主体ID")
	private String subjectId;

	@Schema(description = "未通过原因：PARAM_INVALID/ALREADY_PAID/ORG_NOT_FOUND/ORG_NOT_APPROVED/ORG_DISABLED/ERROR")
	private String reason;

}
