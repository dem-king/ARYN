
package com.aryn.cloud.product.dubbo;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aryn.cloud.product.api.entity.GoodsAppraise;
import com.aryn.cloud.product.api.remote.RemoteGoodsAppraiseService;
import com.aryn.cloud.product.service.IGoodsAppraiseService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author 雨滴kian
 * @date 2024/11/22
 */
@Service
@DubboService
@RequiredArgsConstructor
public class RemoteGoodsAppraiseServiceImpl implements RemoteGoodsAppraiseService {

	private final IGoodsAppraiseService goodsAppraiseService;

	@Override
	public boolean addGoodsAppraise(List<GoodsAppraise> goodsAppraiseList) {
		return goodsAppraiseService.add(goodsAppraiseList);
	}

	@Override
	public List<GoodsAppraise> listByOrderId(String orderId) {
		if (!StringUtils.hasText(orderId)) {
			return new ArrayList<>();
		}
		return goodsAppraiseService.list(Wrappers.<GoodsAppraise>lambdaQuery()
			.eq(GoodsAppraise::getOrderId, orderId));
	}

	@Override
	public GoodsAppraise getById(String id) {
		if (!StringUtils.hasText(id)) {
			return null;
		}
		return goodsAppraiseService.getById(id);
	}

	@Override
	public boolean updateById(GoodsAppraise goodsAppraise) {
		return goodsAppraise != null && StringUtils.hasText(goodsAppraise.getId())
				&& goodsAppraiseService.updateById(goodsAppraise);
	}

	@Override
	public boolean removeById(String id) {
		return StringUtils.hasText(id) && goodsAppraiseService.removeById(id);
	}

	@Override
	public long countNegativeAppraise(LocalDateTime startTime, LocalDateTime endTime) {
		return goodsAppraiseService.countNegativeAppraise(startTime, endTime);
	}

}
