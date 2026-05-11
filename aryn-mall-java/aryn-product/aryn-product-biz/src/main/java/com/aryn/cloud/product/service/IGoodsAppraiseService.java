
package com.aryn.cloud.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.product.api.entity.GoodsAppraise;
import com.aryn.cloud.product.api.vo.AppraiseCountVO;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 商品评价
 *
 * @author 雨滴kian
 * @since 2022/2/26 16:36
 */
public interface IGoodsAppraiseService extends IService<GoodsAppraise> {

	/**
	 * 用户发表评论
	 * @param listGoodsAppraise
	 * @return
	 */
	boolean add(List<GoodsAppraise> listGoodsAppraise);

	/**
	 * 数量查询
	 * @param goodsAppraise
	 * @return
	 */
	AppraiseCountVO getCount(GoodsAppraise goodsAppraise);

	IPage<GoodsAppraise> getPage(Page page, GoodsAppraise goodsAppraise);

	/**
	 * 回复评价
	 * @param goodsAppraise
	 * @return
	 */
	boolean reply(GoodsAppraise goodsAppraise);

	/**
	 * 统计负面评价数量 (1, 2星)
	 * @param startTime 开始时间
	 * @param endTime 结束时间
	 * @return 负面评价数量
	 */
	long countNegativeAppraise(LocalDateTime startTime, LocalDateTime endTime);

}
