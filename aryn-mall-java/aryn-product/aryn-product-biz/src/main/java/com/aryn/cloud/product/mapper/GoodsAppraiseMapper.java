
package com.aryn.cloud.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aryn.cloud.product.api.entity.GoodsAppraise;
import com.aryn.cloud.product.api.vo.AppraiseCountVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 商品评价
 *
 * @author 雨滴kian
 * @since 2022/3/10 17:09
 */
@Mapper
public interface GoodsAppraiseMapper extends BaseMapper<GoodsAppraise> {

	/**
	 * 评论数量查询
	 * @param goodsAppraise
	 * @return
	 */
	AppraiseCountVO selectGoodsAppraiseCount(@Param("query") GoodsAppraise goodsAppraise);

	IPage<GoodsAppraise> selectAppraisePage(Page page, @Param("query") GoodsAppraise goodsAppraise);

}
