package com.aryn.cloud.user.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * C端储值方案VO
 *
 * <p>只暴露展示字段：储值配置实体带 createBy/tenantId/delFlag 等内部字段，
 * 不能直接回给 C 端。
 *
 * @author 雨滴kian
 */
@Data
public class AppRechargeConfigVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "主键")
	private String id;

	@Schema(description = "充值金额")
	private BigDecimal rechargeAmount;

	@Schema(description = "赠送金额")
	private BigDecimal giftAmount;

	@Schema(description = "赠送积分")
	private Integer giftPoint;

	@Schema(description = "排序号")
	private Integer sortOrder;

}
