package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.api.vo.FrequentPurchaseVO;
import com.aryn.cloud.order.mapper.OrderItemMapper;
import com.aryn.cloud.promotion.api.remote.RemoteBuyerProfileService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/** 买家画像远程实现：常购 SKU 判定（近 90 天有效订单统计）。 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteBuyerProfileServiceImpl implements RemoteBuyerProfileService {

	private static final int FREQUENT_LIMIT = 100;

	private final OrderItemMapper orderItemMapper;

	@Override
	public List<String> frequentSkuIds(String tenantId, String userId) {
		if (tenantId == null || userId == null) {
			return List.of();
		}
		List<FrequentPurchaseVO> frequent = orderItemMapper.selectFrequentPurchase(tenantId, userId,
				LocalDateTime.now().minusDays(90), FREQUENT_LIMIT);
		return frequent.stream().map(FrequentPurchaseVO::getSkuId).toList();
	}

}
