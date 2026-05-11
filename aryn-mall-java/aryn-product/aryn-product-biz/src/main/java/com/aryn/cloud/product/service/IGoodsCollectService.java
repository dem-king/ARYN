
package com.aryn.cloud.product.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.product.api.entity.GoodsCollect;
import com.aryn.cloud.product.api.vo.GoodsCollectVO;

import java.util.List;

public interface IGoodsCollectService extends IService<GoodsCollect> {

	/**
	 * 分页查询收藏
	 * @param page
	 * @param userCollect
	 * @return
	 */
	IPage<GoodsCollectVO> getPage(Page page, GoodsCollect userCollect);

	/**
	 * 保存收藏
	 * @param userCollect
	 * @return
	 */
	GoodsCollect saveCollect(GoodsCollect userCollect);

	/**
	 * 批量收藏
	 * @param goodsCollectList
	 * @return
	 */
	boolean saveBatchCollect(List<GoodsCollect> goodsCollectList);

}
