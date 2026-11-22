package com.aryn.cloud.promotion.api.dto;
import lombok.Data;
import java.io.Serializable;
import java.math.BigDecimal;
/** 定向满减券规则，尚未创建模板或发放；有效期和目标继承活动包。 */
@Data
public class CampaignCouponDraftDTO implements Serializable {
	private String name;
	private BigDecimal amount;
	private BigDecimal threshold;
	private Integer quantity;
}
