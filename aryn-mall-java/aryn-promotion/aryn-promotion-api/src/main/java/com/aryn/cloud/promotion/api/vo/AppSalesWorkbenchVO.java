package com.aryn.cloud.promotion.api.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 小程序销售工作台汇总
 *
 * @author 雨滴kian
 * @since 2026/11/5
 */
@Data
@Schema(description = "小程序销售工作台汇总")
public class AppSalesWorkbenchVO implements Serializable {

	@Serial
	private static final long serialVersionUID = 1L;

	@Schema(description = "当前会员是否已登记为在职销售")
	private Boolean isSales;

	@Schema(description = "销售ID")
	private String staffId;

	@Schema(description = "销售姓名")
	private String staffName;

	@Schema(description = "部门名称")
	private String deptName;

	@Schema(description = "销售本人的小程序会员ID（生成推广码用）")
	private String memberUserId;

	@Schema(description = "名下客户数")
	private Long customerCount;

	@Schema(description = "本月成交单数")
	private Integer monthOrderCount;

	@Schema(description = "本月成交额")
	private BigDecimal monthTradeAmount;

	@Schema(description = "本月预估提成")
	private BigDecimal monthCommission;

	@Schema(description = "累计已产生提成")
	private BigDecimal totalCommission;

	@Schema(description = "推广码图片（data URI，可为空）")
	private String qrcodeUrl;

}
