package com.aryn.cloud.order.api.remote;

import com.aryn.cloud.order.api.vo.OrderScenarioFactsVO;
import com.aryn.cloud.promotion.api.vo.CampaignOrderComparisonVO;
import com.aryn.cloud.promotion.api.dto.PromotionContextDTO;
import com.aryn.cloud.promotion.api.vo.PromotionCalculationVO;

/** 仅供受控营销模拟服务调用；capture校验主体并只读，compare只消费已捕获值。 */
public interface RemoteOrderScenarioService {
	OrderScenarioFactsVO capture(PromotionContextDTO requested, String couponUserId);
	CampaignOrderComparisonVO compare(OrderScenarioFactsVO facts, PromotionCalculationVO baseline,
									  PromotionCalculationVO proposed);
}
