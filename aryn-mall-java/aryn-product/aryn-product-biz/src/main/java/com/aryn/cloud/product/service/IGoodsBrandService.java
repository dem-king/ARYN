package com.aryn.cloud.product.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.aryn.cloud.product.api.entity.GoodsBrand;
import com.aryn.cloud.product.api.entity.GoodsSpu;
import com.aryn.cloud.product.api.vo.GoodsBrandFilterVO;

import java.util.List;

public interface IGoodsBrandService extends IService<GoodsBrand> {

	/**
	 * C 端品牌筛选项：只返回在当前查询条件下确有在售商品的品牌。
	 *
	 * @param query 查询条件（categoryFirstId / categorySecondId / name），语义与商品列表一致
	 * @return 品牌 + 商品数；无任何命中时返回空列表（调用方据此隐藏整条品牌筛选）
	 */
	List<GoodsBrandFilterVO> listFilterOptions(GoodsSpu query);

}
