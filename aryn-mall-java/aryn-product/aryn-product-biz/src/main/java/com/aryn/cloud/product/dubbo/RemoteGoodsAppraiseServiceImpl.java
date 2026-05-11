
package com.aryn.cloud.product.dubbo;

import com.aryn.cloud.product.api.entity.GoodsAppraise;
import com.aryn.cloud.product.api.remote.RemoteGoodsAppraiseService;
import com.aryn.cloud.product.service.IGoodsAppraiseService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
	public long countNegativeAppraise(LocalDateTime startTime, LocalDateTime endTime) {
		return goodsAppraiseService.countNegativeAppraise(startTime, endTime);
	}

}
