
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
	 * 查询订单下当前有效评价（逻辑删除的不返回），用于部分评价幂等与订单评价状态判定
	 * @param orderId 订单主键
	 * @return 该订单下的有效评价列表
	 */
	List<GoodsAppraise> listByOrderId(String orderId);

	/**
	 * 按主键查询评价
	 * @param id 评价主键
	 * @return 评价；不存在或已删除返回 null
	 */
	GoodsAppraise getById(String id);

	/**
	 * 按主键更新评价（追评/修改写回）
	 * @param goodsAppraise 评价
	 * @return 是否更新成功
	 */
	boolean updateById(GoodsAppraise goodsAppraise);

	/**
	 * 按主键逻辑删除评价
	 * @param id 评价主键
	 * @return 是否删除成功
	 */
	boolean removeById(String id);

	/**
	 * 统计负面评价数量 (1, 2星)
	 * @param startTime 开始时间
	 * @param endTime 结束时间
	 * @return 负面评价数量
	 */
	long countNegativeAppraise(LocalDateTime startTime, LocalDateTime endTime);

}
