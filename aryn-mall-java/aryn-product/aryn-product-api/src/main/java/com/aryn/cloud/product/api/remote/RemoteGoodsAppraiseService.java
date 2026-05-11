
package com.aryn.cloud.product.api.remote;

import com.aryn.cloud.product.api.entity.GoodsAppraise;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author 雨滴kian
 */
public interface RemoteGoodsAppraiseService {

	/**
	 * this is addGoodsAppraise method
	 * @param goodsAppraiseList
	 * @return
	 */
	boolean addGoodsAppraise(List<GoodsAppraise> goodsAppraiseList);

	/**
	 * 统计负面评价数量 (1, 2星)
	 * @param startTime 开始时间
	 * @param endTime 结束时间
	 * @return 负面评价数量
	 */
	long countNegativeAppraise(LocalDateTime startTime, LocalDateTime endTime);

}
