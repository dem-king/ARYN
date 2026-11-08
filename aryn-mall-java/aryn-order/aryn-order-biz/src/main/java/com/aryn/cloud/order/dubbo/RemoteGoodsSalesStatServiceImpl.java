package com.aryn.cloud.order.dubbo;

import com.aryn.cloud.order.api.dto.GoodsSpuSalesStatDTO;
import com.aryn.cloud.order.api.remote.RemoteGoodsSalesStatService;
import com.aryn.cloud.order.service.ISpuSalesStatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * SPU 社证统计远程实现：读取 T+1 快照最新一期，供商品域卡面社证文案消费。
 *
 * @author aryn
 * @since 2026/11/08
 */
@Slf4j
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteGoodsSalesStatServiceImpl implements RemoteGoodsSalesStatService {

	private final ISpuSalesStatService spuSalesStatService;

	@Override
	public List<GoodsSpuSalesStatDTO> latestStats(String tenantId, List<String> spuIds) {
		// fail-closed：租户缺失直接返回空，绝不退化成跨租户统计
		if (!StringUtils.hasText(tenantId)) {
			return List.of();
		}
		try {
			return spuSalesStatService.latestStats(tenantId, spuIds);
		}
		catch (Exception ex) {
			log.warn("SPU社证统计远程查询失败，降级空列表 tenant={}: {}", tenantId, ex.getMessage());
			return List.of();
		}
	}

}
