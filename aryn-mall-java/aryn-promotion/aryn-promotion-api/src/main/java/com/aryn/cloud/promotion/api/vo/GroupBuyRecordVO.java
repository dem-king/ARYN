package com.aryn.cloud.promotion.api.vo;

import com.aryn.cloud.promotion.api.entity.GroupBuyRecord;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class GroupBuyRecordVO extends GroupBuyRecord {

	@Schema(description = "当前用户是否已参与该团")
	private Boolean isJoined;

	@Schema(description = "当前用户在该团的参团状态：0待付款,1已付款,2已取消；未参与为空")
	private String myMemberStatus;

	@Schema(description = "当前用户在该团已创建的订单ID（已下单待付款时用于跳转订单详情继续支付；未下单为空）")
	private String myOrderId;
}
